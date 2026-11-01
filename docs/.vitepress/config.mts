import { defineConfig } from 'vitepress'
import { withMermaid } from 'vitepress-plugin-mermaid'
import llmstxt, { copyOrDownloadAsMarkdownButtons } from 'vitepress-plugin-llms'

export default withMermaid(
  defineConfig({
    title: 'Observe-It',
    description: 'Observability reference system for Kotlin/Spring Boot with OpenTelemetry and OpenSearch',
    base: '/observe-it/',
    cleanUrls: true,
    srcExclude: ['agents/**'],
    vite: {
      plugins: [
        llmstxt({
          ignoreFiles: ['agents/**'],
          excludeIndexPage: false,
        }),
      ],
    },
    markdown: {
      config(md) {
        md.use(copyOrDownloadAsMarkdownButtons)
      },
    },
    themeConfig: {
      search: {
        provider: 'local',
      },
      outline: {
        level: [2, 3],
      },
      nav: [
        { text: 'Overview', link: '/' },
      ],
      sidebar: [
        {
          text: 'Overview',
          items: [
            { text: 'Overview & Quick Start', link: '/' },
          ],
        },
      ],
      socialLinks: [
        { icon: 'github', link: 'https://github.com/PawelBalcerek/observe-it' },
      ],
    },
  }),
)
