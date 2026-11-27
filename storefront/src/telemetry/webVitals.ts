import { logs, SeverityNumber } from "@opentelemetry/api";
import { type Metric, onCLS, onFCP, onINP, onLCP, onTTFB } from "web-vitals";
import { TELEMETRY_CONFIG } from "./config.ts";

export const reportWebVital = (metric: Metric): void => {
  const logger = logs.getLogger(TELEMETRY_CONFIG.loggers.webVitals);
  const url = metric.navigationURL ? new URL(metric.navigationURL, window.location.href) : null;

  logger.emit({
    eventName: TELEMETRY_CONFIG.events.webVital,
    severityNumber: SeverityNumber.INFO,
    severityText: "INFO",
    body: `Web Vital ${metric.name}: ${metric.value} (${metric.rating})`,
    attributes: {
      "browser.web_vital.name": metric.name,
      "browser.web_vital.value": metric.value,
      "browser.web_vital.delta": metric.delta,
      "browser.web_vital.id": metric.id,
      "browser.web_vital.rating": metric.rating,
      "browser.web_vital.navigation_type": metric.navigationType,
      "url.full": url?.href ?? window.location.href,
      "url.path": url?.pathname ?? window.location.pathname,
    },
  });
};

export const registerWebVitals = (): void => {
  onLCP(reportWebVital);
  onCLS(reportWebVital);
  onINP(reportWebVital);
  onFCP(reportWebVital);
  onTTFB(reportWebVital);
};
