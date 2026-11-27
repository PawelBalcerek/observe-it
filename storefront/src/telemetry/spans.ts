import { type Context, context, SpanStatusCode, trace } from "@opentelemetry/api";
import { TELEMETRY_CONFIG } from "./config.ts";

const activeSpanContextStack: Context[] = [];

export const getActiveDomainSpanContext = (): Context | undefined =>
  activeSpanContextStack[activeSpanContextStack.length - 1];

export const recordSpanError = (
  error: unknown,
  activeContext: Context = context.active(),
): void => {
  const activeSpan = trace.getSpan(activeContext);
  if (!activeSpan) {
    return;
  }

  const normalizedError = error instanceof Error ? error : new Error(String(error));
  activeSpan.recordException(normalizedError);
  activeSpan.setStatus({
    code: SpanStatusCode.ERROR,
    message: normalizedError.message,
  });
};

const withActiveSpan = async <T>(spanName: string, fn: () => Promise<T>): Promise<T> => {
  const tracer = trace.getTracer(TELEMETRY_CONFIG.tracerName);

  return tracer.startActiveSpan(spanName, async (span) => {
    const spanContext = trace.setSpan(context.active(), span);
    activeSpanContextStack.push(spanContext);

    try {
      return await fn();
    } catch (error: unknown) {
      recordSpanError(error, spanContext);
      throw error;
    } finally {
      const index = activeSpanContextStack.lastIndexOf(spanContext);
      if (index !== -1) {
        activeSpanContextStack.splice(index, 1);
      }
      span.end();
    }
  });
};

export const withCheckoutStartSpan = <T>(fn: () => Promise<T>): Promise<T> =>
  withActiveSpan(TELEMETRY_CONFIG.spans.checkoutStart, fn);

export const withCheckoutCompleteSpan = <T>(fn: () => Promise<T>): Promise<T> =>
  withActiveSpan(TELEMETRY_CONFIG.spans.checkoutComplete, fn);
