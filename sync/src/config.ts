/** Central configuration, read once from the environment. */

function num(name: string, def: number): number {
  const v = process.env[name]
  const n = v ? Number(v) : NaN
  return Number.isFinite(n) ? n : def
}

function bool(name: string, def: boolean): boolean {
  const v = process.env[name]
  if (v == null) return def
  return v === 'true' || v === '1'
}

export const config = {
  env: process.env.NODE_ENV ?? 'development',
  port: num('PORT', 4000),

  auth: {
    secret: process.env.JWT_SECRET ?? 'dev-shared-secret-change-me',
    alg: (process.env.JWT_ALG ?? 'HS256') as 'HS256' | 'RS256',
    publicKey: process.env.JWT_PUBLIC_KEY,
    devAllowAnon: bool('DEV_ALLOW_ANON', false),
  },

  persistence: {
    databaseUrl: process.env.DATABASE_URL || '',
    debounceMs: num('PERSIST_DEBOUNCE_MS', 2000),
  },

  scaling: {
    redisUrl: process.env.REDIS_URL || '',
  },

  limits: {
    maxMessageBytes: num('MAX_MESSAGE_BYTES', 1024 * 1024),
    awarenessThrottleMs: num('AWARENESS_THROTTLE_MS', 50),
    docIdleEvictMs: num('DOC_IDLE_EVICT_MS', 60_000),
  },
} as const

export type Config = typeof config
