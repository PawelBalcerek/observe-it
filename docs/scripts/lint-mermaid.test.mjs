import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import {
  extractMermaidBlocks,
  findMarkdownFiles,
  validateMermaidDiagram,
  lintMermaid,
} from './lint-mermaid.mjs'

test('extractMermaidBlocks extracts ```mermaid fenced blocks with 1-based start line numbers', () => {
  const markdown = [
    '# Heading',
    '',
    'Text before diagram',
    '',
    '```mermaid',
    'flowchart TD',
    '  A --> B',
    '```',
    '',
    'Another block of regular code:',
    '```kotlin',
    'val x = 1',
    '```',
    '',
    '```mermaid',
    'sequenceDiagram',
    '  Alice->>Bob: Hello',
    '```',
  ].join('\n')

  const blocks = extractMermaidBlocks(markdown)
  assert.equal(blocks.length, 2)
  assert.equal(blocks[0].line, 5)
  assert.equal(blocks[0].code, 'flowchart TD\n  A --> B')
  assert.equal(blocks[1].line, 15)
  assert.equal(blocks[1].code, 'sequenceDiagram\n  Alice->>Bob: Hello')
})

test('extractMermaidBlocks ignores inline code and four-backtick references', () => {
  const markdown = [
    'Use fenced ```` ```mermaid ```` blocks in guides.',
    'Or inline ````mermaid```` code.',
  ].join('\n')

  const blocks = extractMermaidBlocks(markdown)
  assert.equal(blocks.length, 0)
})

test('findMarkdownFiles scans repository markdown files matching globs', () => {
  const docsDir = path.resolve(import.meta.dirname, '..')
  const files = findMarkdownFiles(docsDir)
  assert.ok(files.length >= 20)
  assert.ok(files.some((f) => f.endsWith('README.md')))
  assert.ok(files.some((f) => f.endsWith('index.md')))
  assert.ok(!files.some((f) => f.includes('node_modules')))
})

test('validateMermaidDiagram resolves for valid mermaid diagram with node labels and brackets', async () => {
  const code = 'flowchart LR\n  TG["Traffic Generator (:8081)"] --> OA["Observed App (:8080)"]'
  await assert.doesNotReject(async () => {
    await validateMermaidDiagram(code)
  })
})

test('validateMermaidDiagram rejects for malformed mermaid diagram with syntax error message', async () => {
  const code = 'flowchart TD\n  A --->>>> B ???'
  await assert.rejects(
    async () => {
      await validateMermaidDiagram(code)
    },
    (err) => {
      assert.match(err.message, /Parse error/i)
      return true
    },
  )
})

test('lintMermaid reports file, line, and syntax error for invalid diagram', async () => {
  const testMarkdown = [
    '# Test Page',
    '',
    '```mermaid',
    'flowchart TD',
    '  A --->>>> B ???',
    '```',
  ].join('\n')

  const tempFilePath = path.resolve(import.meta.dirname, 'temp-invalid-diagram.md')
  fs.writeFileSync(tempFilePath, testMarkdown, 'utf8')
  try {
    const result = await lintMermaid({
      files: [tempFilePath],
    })
    assert.equal(result.totalFiles, 1)
    assert.equal(result.totalDiagrams, 1)
    assert.equal(result.errors.length, 1)
    assert.equal(result.errors[0].line, 3)
    assert.match(result.errors[0].error, /Parse error/i)
  } finally {
    fs.unlinkSync(tempFilePath)
  }
})

