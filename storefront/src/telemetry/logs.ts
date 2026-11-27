import { type AnyValue, context, logs, SeverityNumber } from "@opentelemetry/api";
import { TELEMETRY_CONFIG } from "./config.ts";
import { recordSpanError } from "./spans.ts";

export type TelemetryLogSeverity = "WARN" | "ERROR";
type TelemetryAttributes = Record<string, AnyValue>;

const toSeverityNumber = (severity: TelemetryLogSeverity): SeverityNumber =>
  severity === "ERROR" ? SeverityNumber.ERROR : SeverityNumber.WARN;

const currentUrlAttributes = (): TelemetryAttributes => ({
  "url.full": window.location.href,
  "url.path": window.location.pathname,
});

const toExceptionAttributes = (error: unknown): TelemetryAttributes => {
  if (error instanceof Error) {
    const attributes: TelemetryAttributes = {
      "exception.type": error.name || "Error",
      "exception.message": error.message,
    };
    if (error.stack) {
      attributes["exception.stacktrace"] = error.stack;
    }
    return attributes;
  }

  return {
    "exception.type": typeof error,
    "exception.message": String(error),
  };
};

const emitErrorLog = (
  error: unknown,
  body: string,
  source: "react.error_boundary" | "window.error" | "window.unhandledrejection",
  extraAttributes: TelemetryAttributes = {},
): void => {
  const logger = logs.getLogger(TELEMETRY_CONFIG.loggers.errors);
  logger.emit({
    severityNumber: SeverityNumber.ERROR,
    severityText: "ERROR",
    body,
    attributes: {
      ...toExceptionAttributes(error),
      ...currentUrlAttributes(),
      "error.source": source,
      ...extraAttributes,
    },
    context: context.active(),
  });
};

export const recordOrderFailureLog = (
  severity: TelemetryLogSeverity,
  message: string,
  attributes: Record<string, string | number>,
  error?: Error,
): void => {
  const activeContext = context.active();
  recordSpanError(error ?? new Error(message), activeContext);

  const logger = logs.getLogger(TELEMETRY_CONFIG.loggers.checkout);
  logger.emit({
    severityNumber: toSeverityNumber(severity),
    severityText: severity,
    body: message,
    attributes: {
      ...attributes,
      ...currentUrlAttributes(),
    },
    context: activeContext,
  });
};

export const recordRenderError = (error: Error, componentStack?: string): void => {
  const extraAttributes: TelemetryAttributes = componentStack
    ? { "react.component_stack": componentStack }
    : {};
  emitErrorLog(
    error,
    error.message || "Unhandled React render error",
    "react.error_boundary",
    extraAttributes,
  );
};

export const recordUnhandledError = (
  error: unknown,
  source: "window.error" | "window.unhandledrejection",
  fallbackMessage?: string,
): void => {
  const body =
    error instanceof Error
      ? error.message || fallbackMessage || "Unhandled browser error"
      : fallbackMessage || String(error);
  emitErrorLog(error, body, source);
};
