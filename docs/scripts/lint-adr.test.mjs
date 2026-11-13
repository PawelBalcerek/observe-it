import test from 'node:test'
import assert from 'node:assert/strict'
import path from 'node:path'
import { findAdrFiles, validateAdrContent } from './lint-adr.mjs'

const VALID_ADR = [
  '# 0001. Sample Decision Title',
  '',
  '**Status:** Accepted',
  '',
  '## Context',
  '',
  'Some context explaining the architectural problem.',
  '',
  '## Decision',
  '',
  'We adopt X.',
  '',
  '## Considered Options',
  '',
  '1. **Alternative A**:',
  '   Alternative A was rejected because it violates constraint Y.',
  '2. **Alternative B**:',
  '   Alternative B was deferred for a future milestone.',
  '',
  '## Consequences',
  '',
  '- **Good, because** it solves the problem cleanly.',
  '- **Bad, because** it adds operational coupling.',
  '- **Neutral, because** existing interfaces remain unchanged.',
].join('\n')

const VALID_REFS = {
  indexContent: '| [ADR-0001](./0001-sample-decision.md) | Sample Decision Title | Accepted | Summary |',
  vitepressConfigContent: "{ text: '0001: Sample Decision', link: '/adr/0001-sample-decision' }",
}

test('validateAdrContent accepts a well-formed MADR-style ADR', () => {
  const errors = validateAdrContent('docs/adr/0001-sample-decision.md', VALID_ADR, VALID_REFS)
  assert.deepEqual(errors, [])
})

test('validateAdrContent rejects filename or H1 number mismatch', () => {
  const wrongHeader = VALID_ADR.replace('# 0001.', '# 0002.')
  const errors = validateAdrContent('docs/adr/0001-sample-decision.md', wrongHeader, VALID_REFS)
  assert.equal(errors.length, 1)
  assert.match(errors[0].message, /does not match filename prefix/)
})

test('validateAdrContent rejects missing required sections and out-of-order sections', () => {
  const missingOptions = [
    '# 0001. Sample Decision Title',
    '',
    '**Status:** Accepted',
    '',
    '## Context',
    '',
    'Context.',
    '',
    '## Consequences',
    '',
    '- **Good, because** good.',
    '- **Bad, because** bad.',
    '',
    '## Decision',
    '',
    'Decision.',
  ].join('\n')

  const errors = validateAdrContent('docs/adr/0001-sample-decision.md', missingOptions, VALID_REFS)
  assert.ok(errors.some((e) => e.message.includes('## Considered Options')))
  assert.ok(errors.some((e) => e.message.includes('appear in order')))
})

test('validateAdrContent rejects Considered Options without rejected or deferred status', () => {
  const vagueOption = VALID_ADR.replace('Alternative A was rejected because', 'Alternative A was evaluated because')
  const errors = validateAdrContent('docs/adr/0001-sample-decision.md', vagueOption, VALID_REFS)
  assert.equal(errors.length, 1)
  assert.match(errors[0].message, /rejected, deferred, or postponed/)
})

test('validateAdrContent rejects Consequences without Good/Bad/Neutral format or missing Good/Bad', () => {
  const nonMadrConsequences = [
    '# 0001. Sample Decision Title',
    '',
    '**Status:** Accepted',
    '',
    '## Context',
    '',
    'Context.',
    '',
    '## Decision',
    '',
    'Decision.',
    '',
    '## Considered Options',
    '',
    '1. **Option A**: Rejected because X.',
    '',
    '## Consequences',
    '',
    '- **Positive**: Everything is great.',
    '- **Good, because** it works.',
  ].join('\n')

  const errors = validateAdrContent('docs/adr/0001-sample-decision.md', nonMadrConsequences, VALID_REFS)
  assert.ok(errors.some((e) => e.message.includes('- **Good, because** ')))
  assert.ok(errors.some((e) => e.message.includes('at least one "- **Bad, because** ..."')))
})

test('validateAdrContent rejects ADRs missing from index.md or VitePress sidebar config', () => {
  const errors = validateAdrContent('docs/adr/0001-sample-decision.md', VALID_ADR, {
    indexContent: '# ADRs\n',
    vitepressConfigContent: 'sidebar: []',
  })
  assert.equal(errors.length, 2)
  assert.ok(errors.some((e) => e.message.includes('docs/adr/index.md')))
  assert.ok(errors.some((e) => e.message.includes('docs/.vitepress/config.mts')))
})

test('findAdrFiles discovers numbered ADR files and excludes index.md', () => {
  const adrDir = path.resolve(import.meta.dirname, '..', 'adr')
  const files = findAdrFiles(adrDir)
  assert.ok(files.length >= 7)
  assert.ok(files.every((f) => !f.endsWith('index.md')))
})
