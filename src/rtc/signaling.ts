import type { WebSocket } from 'ws'
import type { Role } from '../rbac.js'
import { log } from '../logger.js'

/**
 * WebRTC signaling relay for mesh calls.
 *
 * The collab server never touches media — browsers hold direct RTCPeerConnections
 * and exchange audio/video/screen peer-to-peer. This hub only relays the small JSON
 * control frames (SDP offer/answer, ICE candidates, mic/cam state) between peers in
 * the same room, and maintains the room roster so a joiner knows who to call.
 *
 * Keyed by roomId (not docId): everyone in a room shares one call regardless of
 * which file they have open. Single-node today; cross-node fan-out would add a Redis
 * "rtc:<roomId>" channel mirroring RedisPubSub (see docs / plan).
 */

export interface RtcPeer {
  ws: WebSocket
  sessionId: string
  userId: string
  name: string
  role: Role
}

/** Public identity of a peer, safe to send to other clients. */
interface PeerInfo {
  sessionId: string
  userId: string
  name: string
  role: Role
}

/** Frames a client sends up to be relayed to a specific peer (or broadcast). */
interface ClientFrame {
  type: 'offer' | 'answer' | 'ice' | 'media-state'
  to?: string
  [k: string]: unknown
}

function info(p: RtcPeer): PeerInfo {
  return { sessionId: p.sessionId, userId: p.userId, name: p.name, role: p.role }
}

export class SignalingHub {
  private rooms = new Map<string, Map<string, RtcPeer>>()

  /** Register a peer and tell it who is already here; announce it to the others. */
  join(roomId: string, peer: RtcPeer): void {
    let room = this.rooms.get(roomId)
    if (!room) {
      room = new Map()
      this.rooms.set(roomId, room)
    }

    // Existing peers become the joiner's call targets (newcomer initiates offers).
    const existing = [...room.values()].map(info)
    room.set(peer.sessionId, peer)

    send(peer, { type: 'peers', peers: existing })
    this.broadcast(roomId, peer.sessionId, { type: 'peer-join', peer: info(peer) })
    log.info('rtc join', { roomId, userId: peer.userId, sessionId: peer.sessionId, size: room.size })
  }

  /** Relay one frame from `fromSessionId` to its `to` target, or broadcast it. */
  relay(roomId: string, fromSessionId: string, frame: ClientFrame): void {
    const room = this.rooms.get(roomId)
    if (!room) return

    const stamped = { ...frame, from: fromSessionId }
    if (frame.to) {
      const target = room.get(frame.to)
      if (target) send(target, stamped)
    } else {
      // media-state and other roster-wide updates fan out to everyone else.
      this.broadcast(roomId, fromSessionId, stamped)
    }
  }

  /** Remove a peer and tell the rest it left so they tear down its connection. */
  leave(roomId: string, sessionId: string): void {
    const room = this.rooms.get(roomId)
    if (!room || !room.delete(sessionId)) return
    this.broadcast(roomId, sessionId, { type: 'peer-leave', sessionId })
    if (room.size === 0) this.rooms.delete(roomId)
    log.info('rtc leave', { roomId, sessionId, size: room.size })
  }

  private broadcast(roomId: string, exceptSessionId: string, msg: unknown): void {
    const room = this.rooms.get(roomId)
    if (!room) return
    for (const [sid, peer] of room) {
      if (sid !== exceptSessionId) send(peer, msg)
    }
  }
}

function send(peer: RtcPeer, msg: unknown): void {
  // ws.OPEN === 1; guard against sending to a closing socket.
  if (peer.ws.readyState !== 1) return
  try {
    peer.ws.send(JSON.stringify(msg))
  } catch (err) {
    log.warn('rtc send failed', { sessionId: peer.sessionId, err: String(err) })
  }
}
