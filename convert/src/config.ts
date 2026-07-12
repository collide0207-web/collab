/** Central configuration, read once from the environment. */

function num(name: string, def: number): number {
  const v = process.env[name]
  const n = v ? Number(v) : NaN
  return Number.isFinite(n) ? n : def
}

export const config = {
  env: process.env.NODE_ENV ?? 'development',
  port: num('PORT', 4500),

  // LibreOffice binary name. `soffice` in the official-ish Debian packages.
  sofficeBin: process.env.SOFFICE_BIN ?? 'soffice',

  limits: {
    // Hard cap on an accepted upload. Matches the frontend's 100 MB limit.
    maxUploadBytes: num('MAX_UPLOAD_BYTES', 100 * 1024 * 1024),
    // Kill a conversion that hangs (corrupt file, LO deadlock).
    conversionTimeoutMs: num('CONVERSION_TIMEOUT_MS', 60_000),
  },

  // Browser origins allowed to call this service. Comma-separated.
  corsOrigins: (process.env.CORS_ALLOWED_ORIGINS ?? 'http://localhost:5173')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean),
} as const

export type Config = typeof config
