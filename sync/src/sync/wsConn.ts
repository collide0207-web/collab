import type { WebSocket } from 'ws'
import type { Conn } from './conn.js'
import type { Identity } from '../auth/jwt.js'
import type { Role } from '../rbac.js'

/** WebSocket-backed Conn. Sends binary frames; tolerates a slow/closing socket. */
export class WsConn implements Conn {
  constructor(
    private ws: WebSocket,
    readonly identity: Identity,
    readonly role: Role,
    readonly sessionId: string,
  ) {}

  send(data: Uint8Array): void {
    if (this.ws.readyState !== this.ws.OPEN) return
    this.ws.send(data, (err) => {
      if (err) this.close(1011, 'send error')
    })
  }

  close(code = 1000, reason = ''): void {
    try {
      this.ws.close(code, reason)
    } catch {
      /* already closing */
    }
  }
}
