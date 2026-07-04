import 'dotenv/config' // load .env before config.ts reads process.env
import http from 'node:http'
import { randomUUID } from 'node:crypto'
import { WebSocketServer, type WebSocket } from 'ws'

import { config } from './config.js'
import { log } from './logger.js'
import { verifyToken } from './auth/jwt.js'
import { DevRoleResolver, roomIdOf, clampRole, type RoleResolver } from './rbac.js'
import { DocManager } from './sync/docManager.js'
import { WsConn } from './sync/wsConn.js'
import { MemoryDocStore, type DocStore } from './persistence/store.js'
import { PostgresDocStore } from './persistence/postgres.js'
import { InProcessPubSub, RedisPubSub, type PubSub } from './scaling/pubsub.js'
import { SignalingHub } from './rtc/signaling.js'

async function main() {
  const nodeId = process.env.NODE_ID ?? randomUUID().slice(0, 8)

  // --- wire dependencies (memory/in-process fallbacks when infra is absent) ---
  let store: DocStore
  if (config.persistence.databaseUrl) {
    const pg = new PostgresDocStore(config.persistence.databaseUrl)
    await pg.init()
    store = pg
    log.info('persistence: postgres')
  } else {
    store = new MemoryDocStore()
    log.warn('persistence: in-memory (NOT durable) — set DATABASE_URL for production')
  }

  const pubsub: PubSub = config.scaling.redisUrl
    ? new RedisPubSub(config.scaling.redisUrl, nodeId)
    : new InProcessPubSub()
  log.info(`pubsub: ${config.scaling.redisUrl ? 'redis' : 'in-process (single node)'}`)

  const roles: RoleResolver = new DevRoleResolver()
  const docManager = new DocManager(store, pubsub)
  const signaling = new SignalingHub()

  // --- HTTP server (health) + WS upgrade ---
  const httpServer = http.createServer((req, res) => {
    if (req.url === '/health' || req.url === '/') {
      res.writeHead(200, { 'content-type': 'application/json' })
      res.end(JSON.stringify({ ok: true, nodeId }))
      return
    }
    // ICE servers for WebRTC mesh calls. A seam for injecting TURN later without
    // any frontend change (the client fetches this list before joining a call).
    if (req.url === '/rtc/ice') {
      res.writeHead(200, { 'content-type': 'application/json' })
      res.end(JSON.stringify({ iceServers: config.rtc.iceServers }))
      return
    }
    res.writeHead(404)
    res.end()
  })

  const wss = new WebSocketServer({ noServer: true, maxPayload: config.limits.maxMessageBytes })

  httpServer.on('upgrade', (req, socket, head) => {
    // Two WS endpoints, both token-authed the same way:
    //   /doc/<docId...>  — Yjs document sync (binary)
    //   /rtc/<roomId>    — WebRTC call signaling relay (JSON)
    const url = new URL(req.url ?? '', 'http://localhost')
    const token = url.searchParams.get('token') ?? undefined

    const doc = /^\/doc\/(.+)$/.exec(url.pathname)
    const rtc = /^\/rtc\/(.+)$/.exec(url.pathname)
    if (!doc && !rtc) {
      socket.destroy()
      return
    }

    // The role the client claims (from the invite link). It can only ever DOWNGRADE
    // the resolver's role (clampRole), so it enforces viewer read-only without
    // allowing privilege escalation. The resolver stays the authoritative source —
    // swap DevRoleResolver for an HttpRoleResolver (Control Plane) to make the base
    // role non-spoofable.
    const claimedRole = url.searchParams.get('role')

    ;(async () => {
      if (doc) {
        const docId = decodeURIComponent(doc[1])
        const identity = await verifyToken(token)
        const base = await roles.resolve(identity, roomIdOf(docId))
        if (!base) throw new Error('forbidden')
        const role = clampRole(base, claimedRole)
        wss.handleUpgrade(req, socket, head, (ws) => {
          void onConnection(ws, docId, identity, role, docManager)
        })
      } else {
        // rtc: the path segment is already the roomId (no file component).
        const roomId = decodeURIComponent(rtc![1])
        const identity = await verifyToken(token)
        const base = await roles.resolve(identity, roomId)
        if (!base) throw new Error('forbidden')
        const role = clampRole(base, claimedRole)
        wss.handleUpgrade(req, socket, head, (ws) => {
          onRtcConnection(ws, roomId, identity, role, signaling)
        })
      }
    })().catch((err) => {
      log.warn('upgrade rejected', { err: String(err) })
      socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n')
      socket.destroy()
    })
  })

  httpServer.listen(config.port, () => log.info('collab server listening', { port: config.port, nodeId }))

  // --- graceful shutdown ---
  const shutdown = async () => {
    log.info('shutting down…')
    wss.clients.forEach((c) => c.close(1001, 'server shutdown'))
    await docManager.shutdown()
    await pubsub.close()
    await store.close()
    httpServer.close(() => process.exit(0))
    setTimeout(() => process.exit(0), 5000).unref()
  }
  process.on('SIGINT', shutdown)
  process.on('SIGTERM', shutdown)
}

async function onConnection(
  ws: WebSocket,
  docId: string,
  identity: Awaited<ReturnType<typeof verifyToken>>,
  role: Awaited<ReturnType<RoleResolver['resolve']>>,
  docManager: DocManager,
) {
  const sessionId = randomUUID()
  const conn = new WsConn(ws, identity, role!, sessionId)
  const shared = await docManager.get(docId)
  shared.addConnection(conn)
  log.info('connected', { docId, userId: identity.userId, role, sessionId })

  ws.binaryType = 'arraybuffer'

  // heartbeat: terminate silently-dead sockets
  let alive = true
  ws.on('pong', () => (alive = true))
  const hb = setInterval(() => {
    if (!alive) {
      ws.terminate()
      return
    }
    alive = false
    try {
      ws.ping()
    } catch {
      /* closing */
    }
  }, 30_000)

  ws.on('message', (data: ArrayBuffer | Buffer) => {
    const bytes = data instanceof ArrayBuffer ? new Uint8Array(data) : new Uint8Array(data.buffer, data.byteOffset, data.byteLength)
    try {
      shared.handleMessage(conn, bytes)
    } catch (err) {
      log.warn('message handling error', { docId, err: String(err) })
    }
  })

  ws.on('close', () => {
    clearInterval(hb)
    shared.removeConnection(conn)
    log.info('disconnected', { docId, userId: identity.userId, sessionId })
  })

  ws.on('error', (err) => log.warn('ws error', { docId, err: String(err) }))
}

/**
 * A WebRTC signaling connection. Unlike the doc socket, frames are JSON text — the
 * server only relays SDP/ICE/media-state between peers, it never touches media.
 */
function onRtcConnection(
  ws: WebSocket,
  roomId: string,
  identity: Awaited<ReturnType<typeof verifyToken>>,
  role: NonNullable<Awaited<ReturnType<RoleResolver['resolve']>>>,
  signaling: SignalingHub,
) {
  const sessionId = randomUUID()
  signaling.join(roomId, { ws, sessionId, userId: identity.userId, name: identity.name, role })

  // heartbeat: terminate silently-dead sockets (same policy as the doc socket)
  let alive = true
  ws.on('pong', () => (alive = true))
  const hb = setInterval(() => {
    if (!alive) {
      ws.terminate()
      return
    }
    alive = false
    try {
      ws.ping()
    } catch {
      /* closing */
    }
  }, 30_000)

  ws.on('message', (data: Buffer) => {
    try {
      const frame = JSON.parse(data.toString())
      signaling.relay(roomId, sessionId, frame)
    } catch (err) {
      log.warn('rtc message error', { roomId, sessionId, err: String(err) })
    }
  })

  ws.on('close', () => {
    clearInterval(hb)
    signaling.leave(roomId, sessionId)
  })

  ws.on('error', (err) => log.warn('rtc ws error', { roomId, sessionId, err: String(err) }))
}

main().catch((err) => {
  log.error('fatal', { err: String(err) })
  process.exit(1)
})
