export const TELEMETRY_CONFIG = {
  serviceName: "storefront",
  defaultServiceVersion: "dev",
  deploymentEnvironment: "local",
  otlp: {
    tracesUrl: "/otlp/v1/traces",
    logsUrl: "/otlp/v1/logs",
    ignoreUrlPattern: /\/otlp\//,
  },
  instrumentation: {
    userInteractionEvents: ["click", "submit"] as const,
  },
  tracerName: "storefront.telemetry",
  loggers: {
    checkout: "storefront.checkout",
    errors: "storefront.errors",
    webVitals: "storefront.web_vitals",
  },
  spans: {
    checkoutStart: "storefront.checkout.start",
    checkoutComplete: "storefront.checkout.complete",
  },
  events: {
    webVital: "browser.web_vital",
  },
} as const;
