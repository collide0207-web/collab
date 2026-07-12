import 'dotenv/config'
import http from 'node:http'
import { config } from './config.js'
import { log } from './logger.js'
import { ConversionError, pptxToPdf } from './convert.js'

/**
 * Document conversion service.
 *
 * POST /convert/pptx
 *   Body: raw PPT/PPTX bytes (application/octet-stream or the office MIME type).
 *   Header: X-Filename (optional) — used to pick the .ppt vs .pptx extension.
 *   Response: application/pdf on success; JSON { error } otherwise.
 *
 * GET /health -> { ok: true }
 *
 * Raw-body upload (not multipart) keeps the service dependency-free — no busboy/
 * multer — and mirrors how the frontend adapter posts the file.
 */
function setCors(req: http.IncomingMessage, res: http.ServerResponse) {
  const origin = req.headers.origin
  if (origin && config.corsOrigins.includes(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin)
    res.setHeader('Vary', 'Origin')
  }
  res.setHeader('Access-Control-Allow-Methods', 'POST, GET, OPTIONS')
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-Filename')
}

function sendJson(res: http.ServerResponse, status: number, body: unknown) {
  const payload = JSON.stringify(body)
  res.writeHead(status, { 'content-type': 'application/json' })
  res.end(payload)
}

function readBody(req: http.IncomingMessage, limit: number): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = []
    let size = 0
    req.on('data', (c: Buffer) => {
      size += c.length
      if (size > limit) {
        reject(new ConversionError('File exceeds the maximum upload size.'))
        req.destroy()
        return
      }
      chunks.push(c)
    })
    req.on('end', () => resolve(Buffer.concat(chunks)))
    req.on('error', reject)
  })
}

const server = http.createServer(async (req, res) => {
  setCors(req, res)

  if (req.method === 'OPTIONS') {
    res.writeHead(204)
    res.end()
    return
  }

  if (req.method === 'GET' && (req.url === '/health' || req.url === '/')) {
    sendJson(res, 200, { ok: true, service: 'convert' })
    return
  }

  if (req.method === 'POST' && req.url === '/convert/pptx') {
    try {
      const body = await readBody(req, config.limits.maxUploadBytes)
      if (body.length === 0) {
        sendJson(res, 400, { error: 'Empty request body.' })
        return
      }
      const name = (req.headers['x-filename'] as string) || 'upload.pptx'
      const pdf = await pptxToPdf(body, name)
      res.writeHead(200, {
        'content-type': 'application/pdf',
        'content-length': pdf.length,
        'cache-control': 'no-store',
      })
      res.end(pdf)
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Conversion failed.'
      log.error('convert: request failed', { err: message })
      sendJson(res, err instanceof ConversionError ? 422 : 500, { error: message })
    }
    return
  }

  sendJson(res, 404, { error: 'Not found.' })
})

server.listen(config.port, () => {
  log.info('convert service listening', { port: config.port, soffice: config.sofficeBin })
})
