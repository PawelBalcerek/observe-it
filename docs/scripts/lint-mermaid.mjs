import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import DOMPurify from 'dompurify'
import mermaid from 'mermaid'

// In Node.js, dompurify exports a factory function without default instance methods.
// Mermaid's flowchart/label sanitizer expects DOMPurify.sanitize and DOMPurify.addHook.
// We shim these on the imported dompurify object (installed transitively with mermaid)
// to achieve zero extra npm dependencies without requiring jsdom or headless browsers.
if (typeof DOMPurify.sanitize !== 'function') {
  DOMPurify.sanitize = (text) => text
}
if (typeof DOMPurify.addHook !== 'function') {
  DOMPurify.addHook = () => {}
}

mermaid.initialize({
  startOnLoad: false,
  suppressErrorRendering: true,
})

/**
 * Extracts all ```mermaid fenced blocks from markdown content with 1-based start line numbers.
 *
 * @param {string} markdownContent
 * @returns {Array<{ line: number, code: string }>}
 */
export function extractMermaidBlocks(markdownContent) {
  const lines = markdownContent.split('\n')
  const blocks = []
  let inBlock = false
  let startLine = 0
  let blockLines = []

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (!inBlock && /^```mermaid(?:\s.*)?$/.test(line)) {
      inBlock = true
      startLine = i + 1
      blockLines = []
    } else if (inBlock && /^```\s*$/.test(line)) {
      inBlock = false
      blocks.push({
        line: startLine,
        code: blockLines.join('\n'),
      })
    } else if (inBlock) {
      blockLines.push(line)
    }
  }

  return blocks
}

/**
 * Validates a single Mermaid diagram string via mermaid.parse().
 *
 * @param {string} code
 * @returns {Promise<unknown>}
 */
export async function validateMermaidDiagram(code) {
  return await mermaid.parse(code)
}

/**
 * Discovers all repository Markdown files matching docs/.markdownlint-cli2.jsonc.
 *
 * @param {string} docsDir
 * @returns {string[]}
 */
export function findMarkdownFiles(docsDir) {
  const patterns = ['../*.md', '**/*.md']
  const matched = fs.globSync(patterns, {
    cwd: docsDir,
    exclude: (p) =>
      p.includes('node_modules') ||
      p.includes('.vitepress') ||
      p.includes('.agents'),
  })
  return matched.sort().map((rel) => path.resolve(docsDir, rel))
}

/**
 * Runs Mermaid syntax validation across specified files or repository Markdown files.
 *
 * @param {object} [options]
 * @param {string[]} [options.files]
 * @param {string} [options.docsDir]
 * @param {string} [options.cwd]
 * @returns {Promise<{ totalFiles: number, totalDiagrams: number, errors: Array<{ file: string, line: number, error: string }> }>}
 */
export async function lintMermaid(options = {}) {
  const currentDir = options.cwd || process.cwd()
  const scriptDir = path.dirname(fileURLToPath(import.meta.url))
  const docsDir = options.docsDir || path.resolve(scriptDir, '..')
  const files = options.files || findMarkdownFiles(docsDir)

  let totalDiagrams = 0
  const errors = []

  for (const filePath of files) {
    const content = fs.readFileSync(filePath, 'utf8')
    const blocks = extractMermaidBlocks(content)
    totalDiagrams += blocks.length

    for (const block of blocks) {
      try {
        await validateMermaidDiagram(block.code)
      } catch (err) {
        const displayPath = path.relative(currentDir, filePath) || filePath
        const message = err?.message || String(err)
        errors.push({
          file: displayPath,
          line: block.line,
          error: message,
        })
      }
    }
  }

  return {
    totalFiles: files.length,
    totalDiagrams,
    errors,
  }
}

const isMain =
  process.argv[1] &&
  path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))

if (isMain) {
  try {
    const result = await lintMermaid()
    if (result.errors.length > 0) {
      for (const err of result.errors) {
        process.stderr.write(
          `${err.file}:${err.line}: Mermaid syntax error: ${err.error}\n`,
        )
      }
      process.stderr.write(
        `\nFound ${result.errors.length} Mermaid syntax error(s) across ${result.totalDiagrams} diagram(s) in ${result.totalFiles} file(s).\n`,
      )
      process.exit(1)
    } else {
      process.stdout.write(
        `Checked ${result.totalDiagrams} Mermaid diagram(s) across ${result.totalFiles} Markdown file(s); all valid.\n`,
      )
      process.exit(0)
    }
  } catch (fatalError) {
    process.stderr.write(`Fatal error during Mermaid linting: ${fatalError}\n`)
    process.exit(1)
  }
}
