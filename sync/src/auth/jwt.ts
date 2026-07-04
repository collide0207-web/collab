import { jwtVerify, importSPKI, type JWTPayload, type KeyLike } from 'jose'
import { config } from '../config.js'

/**
 * Verified identity extracted from the JWT. `userId` here is authoritative — it is
 * taken from the signed token and must never be overridden by client-supplied
 * fields (prevents impersonation).
 */
export interface Identity {
  userId: string
  name: string
  anon?: boolean
}

let cachedKey: Uint8Array | KeyLike | null = null

async function getKey(): Promise<Uint8Array | KeyLike> {
  if (cachedKey) return cachedKey
  if (config.auth.alg === 'RS256') {
    if (!config.auth.publicKey) throw new Error('JWT_PUBLIC_KEY required for RS256')
    cachedKey = await importSPKI(config.auth.publicKey, 'RS256')
  } else {
    cachedKey = new TextEncoder().encode(config.auth.secret)
  }
  return cachedKey
}

/**
 * Verify a JWT and return the identity. Throws on any invalid/expired token.
 * In local dev, if DEV_ALLOW_ANON is set and no token is provided, returns an
 * anonymous identity so the server is usable without the Control Plane.
 */
export async function verifyToken(token: string | undefined): Promise<Identity> {
  if (!token) {
    if (config.auth.devAllowAnon) {
      const id = 'anon-' + Math.random().toString(36).slice(2, 10)
      return { userId: id, name: 'Anonymous', anon: true }
    }
    throw new Error('missing token')
  }

  const key = await getKey()
  const { payload } = await jwtVerify(token, key, {
    algorithms: [config.auth.alg],
  })
  return identityFromPayload(payload)
}

function identityFromPayload(p: JWTPayload): Identity {
  const userId = (p.sub ?? (p as Record<string, unknown>).userId) as string | undefined
  if (!userId) throw new Error('token missing subject/userId')
  const name = ((p as Record<string, unknown>).name as string) || 'User'
  return { userId, name }
}
