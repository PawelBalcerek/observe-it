import { defineConfig } from 'vitepress'
import llmstxt, { copyOrDownloadAsMarkdownButtons } from 'vitepress-plugin-llms'

export default defineConfig({
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
    optimizeDeps: {
      include: ['mermaid'],
    },
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
        { text: 'Guides', link: '/guides/' },
        { text: 'ADRs', link: '/adr/' },
      ],
      sidebar: [
        {
          text: 'Overview',
          items: [
            { text: 'Overview & Quick Start', link: '/' },
          ],
        },
        {
          text: 'Guides',
          items: [
            { text: 'Overview', link: '/guides/' },
            {
              text: 'Observability Stack & Telemetry Pipeline',
              link: '/guides/observability-stack',
            },
            {
              text: 'Structured Logging in Kotlin & Spring Boot',
              link: '/guides/structured-logging',
            },
            {
              text: 'Metrics & OpenSearch Dashboards in Kotlin & Spring Boot',
              link: '/guides/metrics-and-dashboards',
            },
            {
              text: 'Distributed Tracing & Cross-Signal Correlation in Kotlin & Spring Boot',
              link: '/guides/distributed-tracing',
            },
            {
              text: 'Checkout Scenarios & Dashboards Walkthrough',
              link: '/guides/exploring-telemetry',
            },
          ],
        },
        {
          text: 'Architecture Decisions',
          items: [
            { text: 'Overview', link: '/adr/' },
            {
              text: '0001: Zero-Code OTel Java Agent & Logback Bridge',
              link: '/adr/0001-zero-code-otel-javaagent-and-logback-bridge',
            },
            {
              text: '0002: OTel Collector ss4o & OpenSearch Ingest Pipeline',
              link: '/adr/0002-otel-collector-ss4o-and-opensearch-ingest-pipeline',
            },
            {
              text: '0003: SLF4J 2 Fluent Key-Values & Scoped MDC Correlation',
              link: '/adr/0003-slf4j-fluent-key-values-and-scoped-mdc-correlation',
            },
            {
              text: '0004: Out-of-Process Traffic Generator Service',
              link: '/adr/0004-out-of-process-traffic-generator',
            },
            {
              text: '0005: Bounded Lock-Free In-Memory Order Repository',
              link: '/adr/0005-bounded-in-memory-order-repository',
            },
            {
              text: '0006: OTel API Domain Metrics Port & In-Memory Testing',
              link: '/adr/0006-otel-api-domain-metrics-port-and-in-memory-testing',
            },
            {
              text: '0007: SS4O Metrics Delta Temporality & Normalization',
              link: '/adr/0007-ss4o-metrics-delta-temporality-and-opensearch-normalization',
            },
            {
              text: '0008: OTel Distributed Tracing & Domain Span Ports',
              link: '/adr/0008-otel-distributed-tracing-and-domain-span-ports',
            },
            {
              text: '0009: SS4O Traces Normalization & Dashboards',
              link: '/adr/0009-ss4o-traces-ingest-normalization-and-dashboards-exploration',
            },
            {
              text: '0010: MongoDB Order Persistence & TTL Retention',
              link: '/adr/0010-mongodb-order-persistence-and-ttl-retention',
            },
            {
              text: '0011: OTel MongoDB Telemetry & Noise Suppression',
              link: '/adr/0011-otel-mongodb-client-telemetry-and-span-suppression',
            },
            {
              text: '0012: Prometheus Metrics Store & Collector Temporality Split',
              link: '/adr/0012-prometheus-metrics-store-and-collector-temporality-split',
            },
            {
              text: '0013: Traces-Only Data Prepper & APM Service Map',
              link: '/adr/0013-data-prepper-apm-trace-fanout-and-service-map',
            },
          ],
        },
      ],
      socialLinks: [
        { icon: 'github', link: 'https://github.com/PawelBalcerek/observe-it' },
      ],
    },
  })
