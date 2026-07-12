import { spawn } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import { config } from './config.js'
import { log } from './logger.js'

export class ConversionError extends Error {}

/**
 * Convert a PPT/PPTX buffer to PDF using LibreOffice headless.
 *
 * Each call runs in its own temp directory *and* its own LibreOffice user
 * profile (`-env:UserInstallation`). The private profile is what makes
 * concurrent conversions safe: without it, `soffice` reuses a single shared
 * profile and refuses to start a second instance.
 */
export async function pptxToPdf(input: Buffer, originalName: string): Promise<Buffer> {
  const workDir = await mkdtemp(path.join(os.tmpdir(), 'collide-convert-'))
  const profileDir = path.join(workDir, 'profile')
  const ext = originalName.toLowerCase().endsWith('.ppt') ? 'ppt' : 'pptx'
  const inputPath = path.join(workDir, `input.${ext}`)
  const outputPath = path.join(workDir, 'input.pdf')

  try {
    await writeFile(inputPath, input)
    await runSoffice(inputPath, workDir, profileDir)
    return await readFile(outputPath)
  } catch (err) {
    if (err instanceof ConversionError) throw err
    // A missing output file is the common "LO ran but produced nothing" case.
    throw new ConversionError(
      err instanceof Error ? err.message : 'Conversion failed to produce a PDF.',
    )
  } finally {
    rm(workDir, { recursive: true, force: true }).catch((e) =>
      log.warn('convert: temp cleanup failed', { workDir, err: String(e) }),
    )
  }
}

function runSoffice(inputPath: string, outDir: string, profileDir: string): Promise<void> {
  const args = [
    '--headless',
    '--nologo',
    '--nofirststartwizard',
    '--nodefault',
    '--norestore',
    `-env:UserInstallation=file://${profileDir}`,
    '--convert-to',
    'pdf',
    '--outdir',
    outDir,
    inputPath,
  ]

  return new Promise<void>((resolve, reject) => {
    const id = randomUUID().slice(0, 8)
    const started = Date.now()
    const child = spawn(config.sofficeBin, args, { stdio: ['ignore', 'pipe', 'pipe'] })

    let stderr = ''
    child.stderr.on('data', (d) => (stderr += String(d)))

    const timer = setTimeout(() => {
      child.kill('SIGKILL')
      reject(new ConversionError('Conversion timed out.'))
    }, config.limits.conversionTimeoutMs)

    child.on('error', (e) => {
      clearTimeout(timer)
      reject(new ConversionError(`Could not launch LibreOffice (${config.sofficeBin}): ${e.message}`))
    })

    child.on('close', (code) => {
      clearTimeout(timer)
      if (code === 0) {
        log.info('convert: ok', { id, ms: Date.now() - started })
        resolve()
      } else {
        reject(new ConversionError(`LibreOffice exited with code ${code}. ${stderr.trim()}`))
      }
    })
  })
}
