/** Minimal structured (JSON-line) logger. Swap for pino/winston in production. */

type Level = 'debug' | 'info' | 'warn' | 'error'

function emit(level: Level, msg: string, fields?: Record<string, unknown>) {
  const line = { level, msg, ...fields, t: new Date().toISOString() }
  const out = level === 'error' || level === 'warn' ? process.stderr : process.stdout
  out.write(JSON.stringify(line) + '\n')
}

export const log = {
  debug: (msg: string, f?: Record<string, unknown>) => emit('debug', msg, f),
  info: (msg: string, f?: Record<string, unknown>) => emit('info', msg, f),
  warn: (msg: string, f?: Record<string, unknown>) => emit('warn', msg, f),
  error: (msg: string, f?: Record<string, unknown>) => emit('error', msg, f),
}
