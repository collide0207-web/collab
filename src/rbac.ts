import type { Identity } from './auth/jwt.js'

export type Role = 'owner' | 'editor' | 'viewer'

/** roomId is the part of docId before the first "/". docId = "room_88/src/index.js". */
export function roomIdOf(docId: string): string {
  const i = docId.indexOf('/')
  return i === -1 ? docId : docId.slice(0, i)
}

export function canWrite(role: Role): boolean {
  return role === 'owner' || role === 'editor'
}

const ROLE_RANK: Record<Role, number> = { viewer: 0, editor: 1, owner: 2 }

/**
 * Reconcile the role a client *claims* (e.g. from an invite link) with the role the
 * resolver actually grants. Only ever DOWNGRADES — a client can drop itself to a
 * lower role (a viewer link → viewer) but can never elevate above what the resolver
 * authorizes, so a claimed role can't be used to escalate privilege.
 */
export function clampRole(base: Role, claimed: string | null | undefined): Role {
  if (!claimed || !(claimed in ROLE_RANK)) return base
  const c = claimed as Role
  return ROLE_RANK[c] < ROLE_RANK[base] ? c : base
}

/**
 * Resolves a user's role for a room. In production this calls the Control Plane
 * (Membership service) and caches the result in Redis with a short TTL. The
 * interface is deliberately narrow so the transport can change without touching
 * the sync core.
 */
export interface RoleResolver {
  resolve(identity: Identity, roomId: string): Promise<Role | null>
}

/**
 * Dev resolver: grants 'editor' to everyone (or 'owner' to non-anon). Replace with
 * an HttpRoleResolver that queries the Control Plane. Returning null denies access.
 */
export class DevRoleResolver implements RoleResolver {
  async resolve(identity: Identity, _roomId: string): Promise<Role | null> {
    return identity.anon ? 'editor' : 'owner'
  }
}
