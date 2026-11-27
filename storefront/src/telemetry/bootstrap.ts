import { type Context, context, logs, propagation, trace } from "@opentelemetry/api";
import { ZoneContextManager } from "@opentelemetry/context-zone";
import {
  CompositePropagator,
  W3CBaggagePropagator,
  W3CTraceContextPropagator,
} from "@opentelemetry/core";
import { OTLPLogExporter } from "@opentelemetry/exporter-logs-otlp-http";
import { OTLPTraceExporter } from "@opentelemetry/exporter-trace-otlp-http";
import { registerInstrumentations } from "@opentelemetry/instrumentation";
import { DocumentLoadInstrumentation } from "@opentelemetry/instrumentation-document-load";
import { FetchInstrumentation } from "@opentelemetry/instrumentation-fetch";
import { UserInteractionInstrumentation } from "@opentelemetry/instrumentation-user-interaction";
import { browserDetector } from "@opentelemetry/opentelemetry-browser-detector";
import { detectResources, type Resource, resourceFromAttributes } from "@opentelemetry/resources";
import {
  BatchLogRecordProcessor,
  LoggerProvider,
  type LogRecordProcessor,
} from "@opentelemetry/sdk-logs";
import { BatchSpanProcessor, type SpanProcessor, TracerProvider } from "@opentelemetry/sdk-trace";
import { ATTR_SERVICE_NAME, ATTR_SERVICE_VERSION } from "@opentelemetry/semantic-conventions";
import { TELEMETRY_CONFIG } from "./config.ts";
import { recordUnhandledError } from "./logs.ts";
import { getActiveDomainSpanContext } from "./spans.ts";
import { registerWebVitals } from "./webVitals.ts";

class StorefrontContextManager extends ZoneContextManager {
  override active(): Context {
    const current = super.active();
    if (trace.getSpan(current)) {
      return current;
    }
    return getActiveDomainSpanContext() ?? current;
  }
}

export interface TelemetryBootstrapOptions {
  readonly serviceVersion?: string;
  readonly spanProcessor?: SpanProcessor;
  readonly logRecordProcessor?: LogRecordProcessor;
  readonly enableWebVitals?: boolean;
}

export interface TelemetryHandle {
  readonly shutdown: () => Promise<void>;
}

const resolveServiceVersion = (override?: string): string => {
  if (override && override.trim().length > 0) {
    return override.trim();
  }
  const envVersion = import.meta.env.VITE_APP_VERSION;
  if (typeof envVersion === "string" && envVersion.trim().length > 0) {
    return envVersion.trim();
  }
  return TELEMETRY_CONFIG.defaultServiceVersion;
};

const createStorefrontResource = (serviceVersion?: string): Resource => {
  const baseResource = resourceFromAttributes({
    [ATTR_SERVICE_NAME]: TELEMETRY_CONFIG.serviceName,
    [ATTR_SERVICE_VERSION]: resolveServiceVersion(serviceVersion),
    "deployment.environment.name": TELEMETRY_CONFIG.deploymentEnvironment,
  });

  const detectedResource = detectResources({ detectors: [browserDetector] });
  return baseResource.merge(detectedResource);
};

export const initTelemetry = (options: TelemetryBootstrapOptions = {}): TelemetryHandle => {
  const resource = createStorefrontResource(options.serviceVersion);

  const contextManager = new StorefrontContextManager().enable();
  context.setGlobalContextManager(contextManager);

  const propagator = new CompositePropagator({
    propagators: [new W3CTraceContextPropagator(), new W3CBaggagePropagator()],
  });
  propagation.setGlobalPropagator(propagator);

  const spanProcessor =
    options.spanProcessor ??
    new BatchSpanProcessor({
      exporter: new OTLPTraceExporter({ url: TELEMETRY_CONFIG.otlp.tracesUrl }),
    });
  const tracerProvider = new TracerProvider({
    resource,
    spanProcessors: [spanProcessor],
  });
  trace.setGlobalTracerProvider(tracerProvider);

  const logRecordProcessor =
    options.logRecordProcessor ??
    new BatchLogRecordProcessor({
      exporter: new OTLPLogExporter({ url: TELEMETRY_CONFIG.otlp.logsUrl }),
    });
  const loggerProvider = new LoggerProvider({
    resource,
    processors: [logRecordProcessor],
  });
  logs.setGlobalLoggerProvider(loggerProvider);

  const unregisterInstrumentations = registerInstrumentations({
    tracerProvider,
    instrumentations: [
      new FetchInstrumentation({
        ignoreUrls: [TELEMETRY_CONFIG.otlp.ignoreUrlPattern],
      }),
      new DocumentLoadInstrumentation(),
      new UserInteractionInstrumentation({
        eventNames: [...TELEMETRY_CONFIG.instrumentation.userInteractionEvents],
      }),
    ],
  });

  const handleWindowError = (event: ErrorEvent): void => {
    recordUnhandledError(event.error ?? event.message, "window.error", event.message);
  };

  const handleUnhandledRejection = (event: PromiseRejectionEvent): void => {
    recordUnhandledError(event.reason, "window.unhandledrejection");
  };

  window.addEventListener("error", handleWindowError);
  window.addEventListener("unhandledrejection", handleUnhandledRejection);

  if (options.enableWebVitals !== false) {
    registerWebVitals();
  }

  return {
    shutdown: async (): Promise<void> => {
      window.removeEventListener("error", handleWindowError);
      window.removeEventListener("unhandledrejection", handleUnhandledRejection);
      unregisterInstrumentations();
      await Promise.all([tracerProvider.shutdown(), loggerProvider.shutdown()]);
      trace.disable();
      logs.disable();
      propagation.disable();
      contextManager.disable();
      context.disable();
    },
  };
};
