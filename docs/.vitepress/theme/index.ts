import DefaultTheme from 'vitepress/theme'
import type { Theme } from 'vitepress'
import { h, nextTick, watch } from 'vue'
import { useData } from 'vitepress'
import { createMermaidRenderer } from 'vitepress-mermaid-renderer'
import 'vitepress-mermaid-renderer/css'
import CopyOrDownloadAsMarkdownButtons from 'vitepress-plugin-llms/vitepress-components/CopyOrDownloadAsMarkdownButtons.vue'

const enabledToolbarButtons = {
  zoomIn: 'enabled' as const,
  zoomOut: 'enabled' as const,
  resetView: 'enabled' as const,
  copyCode: 'enabled' as const,
  toggleFullscreen: 'enabled' as const,
  zoomLevel: 'enabled' as const,
  download: 'enabled' as const,
}

export default {
  extends: DefaultTheme,
  Layout: () => {
    const { isDark } = useData()

    const configureRenderer = () => {
      const mermaidRenderer = createMermaidRenderer({
        theme: isDark.value ? 'dark' : 'default',
        startOnLoad: false,
      })

      mermaidRenderer.setToolbar({
        showLanguageLabel: false,
        downloadFormat: 'svg',
        fullscreenMode: 'browser',
        desktop: enabledToolbarButtons,
        fullscreen: enabledToolbarButtons,
      })
    }

    nextTick(() => {
      configureRenderer()
    })

    watch(
      () => isDark.value,
      () => {
        nextTick(() => {
          configureRenderer()
        })
      },
    )

    return h(DefaultTheme.Layout, null, {})
  },
  enhanceApp({ app }) {
    app.component('CopyOrDownloadAsMarkdownButtons', CopyOrDownloadAsMarkdownButtons)
  },
} satisfies Theme
