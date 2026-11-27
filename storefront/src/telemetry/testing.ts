import { SeverityNumber, SpanStatusCode } from "@opentelemetry/api";
import {
  InMemoryLogRecordExporter,
  type ReadableLogRecord,
  SimpleLogRecordProcessor,
} from "@opentelemetry/sdk-logs";
import {
  InMemorySpanExporter,
  type ReadableSpan,
  SimpleSpanProcessor,
} from "@opentelemetry/sdk-trace";
import { expect } from "vitest";
import { initTelemetry, type TelemetryHandle } from "./bootstrap.ts";
import { TELEMETRY_CONFIG } from "./config.ts";
import type { TelemetryLogSeverity } from "./logs.ts";

interface ExpectedResourceAttributes {
  readonly serviceName: string;
  readonly serviceVersion: string;
  readonly environment: string;
}

interface ExpectedSpan {
  readonly name: string;
  readonly status?: "OK_OR_UNSET" | "ERROR";
  readonly hasException?: boolean;
}

interface ExpectedOrderFailureLog {
  readonly severity: TelemetryLogSeverity;
  readonly message: string;
  readonly orderId: string;
  readonly statusCode: number;
  readonly correlatedToSpan: string;
}

interface ExpectedWebVitalLog {
  readonly name: string;
  readonly value: number;
  readonly delta: number;
  readonly id: string;
  readonly rating: string;
  readonly navigationType: string;
}

interface ExpectedRenderErrorLog {
  readonly message: string;
  readonly errorType?: string;
  readonly componentName: string;
}

interface ExpectedUnhandledErrorLog {
  readonly source: "window.error" | "window.unhandledrejection";
  readonly message: string;
}

export interface InMemoryTelemetryHarness {
  readonly expectSpanCount: (count: number) => void;
  readonly expectLogCount: (count: number) => void;
  readonly expectResourceAttributes: (expected: ExpectedResourceAttributes) => void;
  readonly expectSpan: (index: number, expected: ExpectedSpan) => void;
  readonly expectOrderFailureLog: (index: number, expected: ExpectedOrderFailureLog) => void;
  readonly expectWebVitalLog: (expected: ExpectedWebVitalLog) => void;
  readonly expectRenderErrorLog: (expected: ExpectedRenderErrorLog) => void;
  readonly expectUnhandledErrorLog: (index: number, expected: ExpectedUnhandledErrorLog) => void;
  readonly expectNoSpansMatchingUrl: (urlFragment: string) => void;
  readonly getSpanTraceId: (spanName: string) => string;
  readonly shutdown: () => Promise<void>;
}

const findSpanOrFail = (spans: readonly ReadableSpan[], spanName: string): ReadableSpan => {
  const span = spans.find((candidate) => candidate.name === spanName);
  expect(span, `Expected finished span named "${spanName}"`).toBeDefined();
  if (!span) {
    throw new Error(`Span "${spanName}" not found`);
  }
  return span;
};

const findLogByIndexOrFail = (
  logs: readonly ReadableLogRecord[],
  index: number,
): ReadableLogRecord => {
  const record = logs[index];
  expect(record, `Expected log record at index ${index}`).toBeDefined();
  if (!record) {
    throw new Error(`Log record at index ${index} not found`);
  }
  return record;
};

export const createInMemoryTelemetryHarness = (
  serviceVersion = "0.6.0-test",
): InMemoryTelemetryHarness => {
  const spanExporter = new InMemorySpanExporter();
  const logExporter = new InMemoryLogRecordExporter();

  const handle: TelemetryHandle = initTelemetry({
    serviceVersion,
    spanProcessor: new SimpleSpanProcessor({ exporter: spanExporter }),
    logRecordProcessor: new SimpleLogRecordProcessor({ exporter: logExporter }),
    enableWebVitals: false,
  });

  return {
    expectSpanCount: (count: number): void => {
      expect(spanExporter.getFinishedSpans()).toHaveLength(count);
    },

    expectLogCount: (count: number): void => {
      expect(logExporter.getFinishedLogRecords()).toHaveLength(count);
    },

    expectResourceAttributes: ({
      serviceName,
      serviceVersion: expectedVersion,
      environment,
    }: ExpectedResourceAttributes): void => {
      const span = spanExporter.getFinishedSpans()[0];
      expect(span?.resource.attributes).toMatchObject({
        "service.name": serviceName,
        "service.version": expectedVersion,
        "deployment.environment.name": environment,
      });
    },

    expectSpan: (index: number, { name, status, hasException }: ExpectedSpan): void => {
      const span = spanExporter.getFinishedSpans()[index];
      expect(span?.name).toBe(name);

      if (status === "ERROR") {
        expect(span?.status.code).toBe(SpanStatusCode.ERROR);
      } else if (status === "OK_OR_UNSET") {
        expect(span?.status.code).not.toBe(SpanStatusCode.ERROR);
      }

      if (hasException !== undefined) {
        expect(span?.events.some((event) => event.name === "exception")).toBe(hasException);
      }
    },

    expectOrderFailureLog: (
      index: number,
      { severity, message, orderId, statusCode, correlatedToSpan }: ExpectedOrderFailureLog,
    ): void => {
      const span = findSpanOrFail(spanExporter.getFinishedSpans(), correlatedToSpan);
      const record = findLogByIndexOrFail(logExporter.getFinishedLogRecords(), index);

      expect(span.status.code).toBe(SpanStatusCode.ERROR);
      expect(span.events.some((event) => event.name === "exception")).toBe(true);
      expect(record.severityText).toBe(severity);
      expect(record.severityNumber).toBe(
        severity === "ERROR" ? SeverityNumber.ERROR : SeverityNumber.WARN,
      );
      expect(record.body).toBe(message);
      expect(record.attributes).toMatchObject({
        "order.id": orderId,
        "http.response.status_code": statusCode,
      });
      expect(record.spanContext?.traceId).toBe(span.spanContext().traceId);
      expect(record.spanContext?.spanId).toBe(span.spanContext().spanId);
      expect(record.resource.attributes["service.name"]).toBe(TELEMETRY_CONFIG.serviceName);
    },

    expectWebVitalLog: ({
      name,
      value,
      delta,
      id,
      rating,
      navigationType,
    }: ExpectedWebVitalLog): void => {
      const record = logExporter
        .getFinishedLogRecords()
        .find((candidate) => candidate.attributes["browser.web_vital.name"] === name);
      expect(record, `Expected browser.web_vital log for ${name}`).toBeDefined();
      expect(record?.eventName).toBe(TELEMETRY_CONFIG.events.webVital);
      expect(record?.severityText).toBe("INFO");
      expect(record?.severityNumber).toBe(SeverityNumber.INFO);
      expect(record?.body).toContain(name);
      expect(record?.attributes).toMatchObject({
        "browser.web_vital.name": name,
        "browser.web_vital.value": value,
        "browser.web_vital.delta": delta,
        "browser.web_vital.id": id,
        "browser.web_vital.rating": rating,
        "browser.web_vital.navigation_type": navigationType,
        "url.full": window.location.href,
        "url.path": window.location.pathname,
      });
      expect(record?.resource.attributes["service.name"]).toBe(TELEMETRY_CONFIG.serviceName);
    },

    expectRenderErrorLog: ({
      message,
      errorType = "Error",
      componentName,
    }: ExpectedRenderErrorLog): void => {
      const logs = logExporter.getFinishedLogRecords();
      expect(logs).toHaveLength(1);
      const record = findLogByIndexOrFail(logs, 0);

      expect(record.severityText).toBe("ERROR");
      expect(record.severityNumber).toBe(SeverityNumber.ERROR);
      expect(record.body).toBe(message);
      expect(record.attributes).toMatchObject({
        "exception.type": errorType,
        "exception.message": message,
        "error.source": "react.error_boundary",
      });
      expect(String(record.attributes["exception.stacktrace"])).toContain(message);
      expect(String(record.attributes["react.component_stack"])).toContain(componentName);
    },

    expectUnhandledErrorLog: (
      index: number,
      { source, message }: ExpectedUnhandledErrorLog,
    ): void => {
      const record = findLogByIndexOrFail(logExporter.getFinishedLogRecords(), index);
      expect(record.severityText).toBe("ERROR");
      expect(record.severityNumber).toBe(SeverityNumber.ERROR);
      expect(record.body).toBe(message);
      expect(record.attributes["error.source"]).toBe(source);
      expect(String(record.attributes["exception.stacktrace"])).toContain(message);
    },

    expectNoSpansMatchingUrl: (urlFragment: string): void => {
      const hasMatch = spanExporter
        .getFinishedSpans()
        .some((span) => String(span.attributes["url.full"] ?? "").includes(urlFragment));
      expect(hasMatch).toBe(false);
    },

    getSpanTraceId: (spanName: string): string =>
      findSpanOrFail(spanExporter.getFinishedSpans(), spanName).spanContext().traceId,

    shutdown: (): Promise<void> => handle.shutdown(),
  };
};
