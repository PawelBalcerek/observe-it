import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const REQUIRED_SECTIONS = [
  'Context',
  'Decision',
  'Considered Options',
  'Consequences',
]

const CONSEQUENCE_PREFIXES = [
  '- **Good, because** ',
  '- **Bad, because** ',
  '- **Neutral, because** ',
]

const STATUS_PATTERN = /^\*\*Status:\*\*\s+(Accepted|Proposed|Deprecated|Superseded by \[ADR-\d{4}\]\([^)]+\))\s*$/
const OPTION_STATUS_PATTERN = /\b(rejected|deferred|postponed)\b/i

/**
 * Discovers all numbered ADR files (`NNNN-*.md`) in `docs/adr/`.
 *
 * @param {string} adrDir
 * @returns {string[]}
 */
export function findAdrFiles(adrDir) {
  if (!fs.existsSync(adrDir)) {
    return []
  }

  return fs
    .readdirSync(adrDir)
    .filter((name) => name.endsWith('.md') && name !== 'index.md')
    .sort()
    .map((name) => path.resolve(adrDir, name))
}

/**
 * Extracts `## H2` sections with 1-based line numbers and trimmed body text.
 *
 * @param {string[]} lines
 * @returns {Array<{ heading: string, line: number, bodyLines: string[] }>}
 */
function extractH2Sections(lines) {
  const sections = []
  let current = null

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const match = /^##\s+(.+?)\s*$/.exec(line)
    if (match) {
      if (current) {
        sections.push(current)
      }
      current = {
        heading: match[1],
        line: i + 1,
        bodyLines: [],
      }
    } else if (current) {
      current.bodyLines.push(line)
    }
  }

  if (current) {
    sections.push(current)
  }

  return sections
}

/**
 * Validates a single ADR Markdown file against the repository MADR structure.
 *
 * @param {string} filePath
 * @param {string} content
 * @param {{ indexContent?: string, vitepressConfigContent?: string }} [refs]
 * @returns {Array<{ file: string, line: number, message: string }>}
 */
export function validateAdrContent(filePath, content, refs = {}) {
  const errors = []
  const fileName = path.basename(filePath)
  const nameMatch = /^(\d{4})-[a-z0-9-]+\.md$/.exec(fileName)
  if (!nameMatch) {
    errors.push({
      file: fileName,
      line: 1,
      message: `ADR filename must match NNNN-<kebab-case-slug>.md (got "${fileName}")`,
    })
    return errors
  }

  const adrNumber = nameMatch[1]
  const slugWithoutExt = fileName.slice(0, -3)
  const lines = content.split('\n')

  const h1Match = /^#\s+(\d{4})\.\s+(.+?)\s*$/.exec(lines[0] ?? '')
  if (!h1Match) {
    errors.push({
      file: fileName,
      line: 1,
      message: `First line must be "# ${adrNumber}. <Short Decision Title>"`,
    })
  } else if (h1Match[1] !== adrNumber) {
    errors.push({
      file: fileName,
      line: 1,
      message: `H1 ADR number "${h1Match[1]}" does not match filename prefix "${adrNumber}"`,
    })
  }

  const statusLineIndex = lines.findIndex((line) => line.startsWith('**Status:**'))
  if (statusLineIndex === -1) {
    errors.push({
      file: fileName,
      line: 1,
      message: 'Missing "**Status:** Accepted" (or Proposed / Deprecated / Superseded by ...) line',
    })
  } else if (!STATUS_PATTERN.test(lines[statusLineIndex])) {
    errors.push({
      file: fileName,
      line: statusLineIndex + 1,
      message: `Invalid Status line: "${lines[statusLineIndex]}"`,
    })
  }

  const sections = extractH2Sections(lines)
  const headings = sections.map((s) => s.heading)

  for (const required of REQUIRED_SECTIONS) {
    if (!headings.includes(required)) {
      errors.push({
        file: fileName,
        line: 1,
        message: `Missing required section "## ${required}"`,
      })
    }
  }

  const requiredOrder = headings.filter((h) => REQUIRED_SECTIONS.includes(h))
  const expectedPresentOrder = REQUIRED_SECTIONS.filter((h) => headings.includes(h))
  if (requiredOrder.join('|') !== expectedPresentOrder.join('|')) {
    errors.push({
      file: fileName,
      line: 1,
      message: `Required sections must appear in order: ${REQUIRED_SECTIONS.map((s) => `## ${s}`).join(' -> ')}`,
    })
  }

  const optionsSection = sections.find((s) => s.heading === 'Considered Options')
  if (optionsSection) {
    const topLevelOptions = []
    let currentOption = null

    for (let i = 0; i < optionsSection.bodyLines.length; i++) {
      const line = optionsSection.bodyLines[i]
      if (/^(?:\d+\.|-)\s+/.test(line)) {
        if (currentOption) {
          topLevelOptions.push(currentOption)
        }
        currentOption = {
          line: optionsSection.line + 1 + i,
          text: line,
        }
      } else if (currentOption && /^\s+\S/.test(line)) {
        currentOption.text += `\n${line}`
      }
    }
    if (currentOption) {
      topLevelOptions.push(currentOption)
    }

    if (topLevelOptions.length === 0) {
      errors.push({
        file: fileName,
        line: optionsSection.line,
        message: '"## Considered Options" must contain at least one numbered or bulleted option',
      })
    } else {
      for (const option of topLevelOptions) {
        if (!OPTION_STATUS_PATTERN.test(option.text)) {
          errors.push({
            file: fileName,
            line: option.line,
            message: 'Each Considered Option must explicitly state whether it was rejected, deferred, or postponed',
          })
        }
      }
    }
  }

  const consequencesSection = sections.find((s) => s.heading === 'Consequences')
  if (consequencesSection) {
    const bulletLines = []
    for (let i = 0; i < consequencesSection.bodyLines.length; i++) {
      const line = consequencesSection.bodyLines[i]
      if (line.startsWith('- ')) {
        bulletLines.push({
          line: consequencesSection.line + 1 + i,
          text: line,
        })
      }
    }

    if (bulletLines.length === 0) {
      errors.push({
        file: fileName,
        line: consequencesSection.line,
        message: '"## Consequences" must be an unordered list of Good, Bad, or Neutral items',
      })
    } else {
      let goodCount = 0
      let badCount = 0

      for (const bullet of bulletLines) {
        const validPrefix = CONSEQUENCE_PREFIXES.find((prefix) => bullet.text.startsWith(prefix))
        if (!validPrefix) {
          errors.push({
            file: fileName,
            line: bullet.line,
            message: 'Each Consequences item must start with "- **Good, because** ", "- **Bad, because** ", or "- **Neutral, because** "',
          })
        } else if (validPrefix.includes('Good')) {
          goodCount++
        } else if (validPrefix.includes('Bad')) {
          badCount++
        }
      }

      if (goodCount === 0) {
        errors.push({
          file: fileName,
          line: consequencesSection.line,
          message: '"## Consequences" must include at least one "- **Good, because** ..." item',
        })
      }
      if (badCount === 0) {
        errors.push({
          file: fileName,
          line: consequencesSection.line,
          message: '"## Consequences" must include at least one "- **Bad, because** ..." item',
        })
      }
    }
  }

  if (typeof refs.indexContent === 'string') {
    const hasIndexLink =
      refs.indexContent.includes(`./${fileName}`) ||
      refs.indexContent.includes(`(${fileName})`)
    if (!hasIndexLink) {
      errors.push({
        file: fileName,
        line: 1,
        message: `ADR is not registered in docs/adr/index.md (expected link to ${fileName})`,
      })
    }
  }

  if (typeof refs.vitepressConfigContent === 'string') {
    const expectedLink = `/adr/${slugWithoutExt}`
    if (!refs.vitepressConfigContent.includes(expectedLink)) {
      errors.push({
        file: fileName,
        line: 1,
        message: `ADR is not registered in docs/.vitepress/config.mts (expected "${expectedLink}")`,
      })
    }
  }

  return errors
}

/**
 * Validates all ADR files in `docs/adr/`.
 *
 * @param {object} [options]
 * @param {string} [options.docsDir]
 * @param {string} [options.cwd]
 * @returns {{ totalFiles: number, errors: Array<{ file: string, line: number, message: string }> }}
 */
export function lintAdrs(options = {}) {
  const currentDir = options.cwd || process.cwd()
  const scriptDir = path.dirname(fileURLToPath(import.meta.url))
  const docsDir = options.docsDir || path.resolve(scriptDir, '..')
  const adrDir = path.resolve(docsDir, 'adr')
  const indexPath = path.resolve(adrDir, 'index.md')
  const configPath = path.resolve(docsDir, '.vitepress', 'config.mts')

  const indexContent = fs.existsSync(indexPath) ? fs.readFileSync(indexPath, 'utf8') : undefined
  const vitepressConfigContent = fs.existsSync(configPath) ? fs.readFileSync(configPath, 'utf8') : undefined

  const files = findAdrFiles(adrDir)
  const errors = []

  for (const filePath of files) {
    const content = fs.readFileSync(filePath, 'utf8')
    const fileErrors = validateAdrContent(filePath, content, {
      indexContent,
      vitepressConfigContent,
    })
    for (const err of fileErrors) {
      errors.push({
        file: path.relative(currentDir, filePath) || err.file,
        line: err.line,
        message: err.message,
      })
    }
  }

  return {
    totalFiles: files.length,
    errors,
  }
}

const isMain =
  process.argv[1] &&
  path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))

if (isMain) {
  try {
    const result = lintAdrs()
    if (result.errors.length > 0) {
      for (const err of result.errors) {
        process.stderr.write(`${err.file}:${err.line}: ADR lint error: ${err.message}\n`)
      }
      process.stderr.write(
        `\nFound ${result.errors.length} ADR lint error(s) across ${result.totalFiles} ADR file(s).\n`,
      )
      process.exit(1)
    } else {
      process.stdout.write(`Checked ${result.totalFiles} ADR file(s); all valid.\n`)
      process.exit(0)
    }
  } catch (fatalError) {
    process.stderr.write(`Fatal error during ADR linting: ${fatalError}\n`)
    process.exit(1)
  }
}
