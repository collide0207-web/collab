import type { Identity } from '../auth/jwt.js'
import type { Role } from '../rbac.js'

/**
 * Transport-agnostic connection. The sync core talks to this interface, so it can be
 * driven by a real WebSocket in production or an in-memory pipe in tests.
 */
export interface Conn {
  readonly identity: Identity
  readonly role: Role
  readonly sessionId: string
  send(data: Uint8Array): void
  close(code?: number, reason?: string): void
}
