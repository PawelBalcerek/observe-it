import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Metric } from "web-vitals";
import { recordOrderFailureLog, withCheckoutCompleteSpan, withCheckoutStartSpan } from "./index.ts";
import { createInMemoryTelemetryHarness, type InMemoryTelemetryHarness } from "./testing.ts";
import { reportWebVital } from "./webVitals.ts";

const aWebVitalMetric = (overrides: Partial<Metric> = {}): Metric => ({
  name: "LCP",
  value: 1420.5,
  delta: 1420.5,
  id: "v6-1700000000000-1234567890",
  rating: "good",
  navigationType: "navigate",
  navigationId: 1,
  entries: [],
  ...overrides,
});

interface CapturedFetchHeaders {
  readonly expectTraceparent: (pathname: string, traceId: string) => void;
  readonly expectNoTraceparent: (pathname: string) => void;
}

const stubFetchHeaders = (): CapturedFetchHeaders => {
  const captured: Record<string, Headers> = {};

  vi.stubGlobal(
    "fetch",
    vi.fn<typeof fetch>(async (input, init) => {
      const rawUrl =
        typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
      const pathname = new URL(rawUrl, window.location.origin).pathname;
      captured[pathname] =
        input instanceof Request ? new Headers(input.headers) : new Headers(init?.headers);
      return new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } });
    }),
  );

  return {
    expectTraceparent: (pathname: string, traceId: string): void => {
      expect(captured[pathname]?.get("traceparent")).toMatch(
        new RegExp(`^00-${traceId}-[0-9a-f]{16}-01$`),
      );
    },
    expectNoTraceparent: (pathname: string): void => {
      expect(captured[pathname]?.get("traceparent")).toBeNull();
    },
  };
};

describe("Storefront telemetry", () => {
  let harness: InMemoryTelemetryHarness;

  beforeEach(() => {
    harness = createInMemoryTelemetryHarness();
  });

  afterEach(async () => {
    await harness.shutdown();
  });

  it("sets storefront resource attributes including service.name, service.version, and deployment.environment.name", async () => {
    // given
    const runOperation = async (): Promise<string> => "ok";

    // when
    await withCheckoutStartSpan(runOperation);

    // then
    harness.expectResourceAttributes({
      serviceName: "storefront",
      serviceVersion: "0.6.0-test",
      environment: "local",
    });
  });

  it.each([
    {
      spanName: "storefront.checkout.start",
      runWithSpan: withCheckoutStartSpan,
      successValue: "reserved",
      errorMessage: "Inventory service unavailable",
    },
    {
      spanName: "storefront.checkout.complete",
      runWithSpan: withCheckoutCompleteSpan,
      successValue: "shipped",
      errorMessage: "Payment gateway timed out",
    },
  ])(
    "wraps $spanName and records exceptions with ERROR status on failure",
    async ({ spanName, runWithSpan, successValue, errorMessage }) => {
      // given
      const boom = new Error(errorMessage);

      // when
      const result = await runWithSpan(async () => successValue);
      await expect(
        runWithSpan(async () => {
          throw boom;
        }),
      ).rejects.toThrow(errorMessage);

      // then
      expect(result).toBe(successValue);
      harness.expectSpanCount(2);
      harness.expectSpan(0, { name: spanName, status: "OK_OR_UNSET" });
      harness.expectSpan(1, { name: spanName, status: "ERROR", hasException: true });
    },
  );

  it("correlates checkout WARN and ERROR logs with active span traceId/spanId and marks the span ERROR", async () => {
    // given
    const outOfStockError = new Error("Item SKU-OUT-OF-STOCK is out of stock");
    const timeoutError = new Error("Payment gateway timed out");

    // when
    await withCheckoutStartSpan(async () => {
      await Promise.resolve();
      recordOrderFailureLog(
        "WARN",
        outOfStockError.message,
        {
          "order.id": "ord-oos",
          "http.response.status_code": 422,
        },
        outOfStockError,
      );
    });

    await withCheckoutCompleteSpan(async () => {
      await Promise.resolve();
      recordOrderFailureLog(
        "ERROR",
        timeoutError.message,
        {
          "order.id": "ord-timeout",
          "payment.method": "TIMEOUT_GATEWAY",
          "http.response.status_code": 504,
        },
        timeoutError,
      );
    });

    // then
    harness.expectLogCount(2);
    harness.expectOrderFailureLog(0, {
      severity: "WARN",
      message: "Item SKU-OUT-OF-STOCK is out of stock",
      orderId: "ord-oos",
      statusCode: 422,
      correlatedToSpan: "storefront.checkout.start",
    });

    // and
    harness.expectOrderFailureLog(1, {
      severity: "ERROR",
      message: "Payment gateway timed out",
      orderId: "ord-timeout",
      statusCode: 504,
      correlatedToSpan: "storefront.checkout.complete",
    });
  });

  it("emits structured browser.web_vital log events for Core Web Vitals", () => {
    // given
    const metrics: readonly Metric[] = [
      aWebVitalMetric({ name: "LCP", value: 1200, delta: 1200, rating: "good" }),
      aWebVitalMetric({ name: "CLS", value: 0.04, delta: 0.04, rating: "good" }),
      aWebVitalMetric({ name: "INP", value: 280, delta: 280, rating: "needs-improvement" }),
      aWebVitalMetric({ name: "FCP", value: 850, delta: 850, rating: "good" }),
      aWebVitalMetric({ name: "TTFB", value: 120, delta: 120, rating: "good" }),
    ];

    // when
    for (const metric of metrics) {
      reportWebVital(metric);
    }

    // then
    harness.expectLogCount(5);
    harness.expectWebVitalLog({
      name: "INP",
      value: 280,
      delta: 280,
      id: "v6-1700000000000-1234567890",
      rating: "needs-improvement",
      navigationType: "navigate",
    });
  });

  it("captures global window error and unhandledrejection events as ERROR logs", () => {
    // given
    const uncaughtError = new Error("Unexpected script failure");
    const rejectionError = new Error("Unhandled promise failure");
    const rejectionEvent = new Event("unhandledrejection") as Event & { reason?: unknown };
    rejectionEvent.reason = rejectionError;

    // when
    window.dispatchEvent(
      new ErrorEvent("error", {
        error: uncaughtError,
        message: uncaughtError.message,
      }),
    );
    window.dispatchEvent(rejectionEvent);

    // then
    harness.expectLogCount(2);
    harness.expectUnhandledErrorLog(0, {
      source: "window.error",
      message: "Unexpected script failure",
    });

    // and
    harness.expectUnhandledErrorLog(1, {
      source: "window.unhandledrejection",
      message: "Unhandled promise failure",
    });
  });

  it("propagates W3C traceparent across sequential same-origin fetch requests and ignores /otlp/ URLs", async () => {
    // given
    await harness.shutdown();
    const fetchHeaders = stubFetchHeaders();
    harness = createInMemoryTelemetryHarness();

    // when
    await withCheckoutStartSpan(async () => {
      await fetch("/api/orders", { method: "POST" });
      await fetch("/api/orders/ord-100/reserve", { method: "POST" });
      await fetch("/otlp/v1/traces", { method: "POST" });
    });

    // then
    const traceId = harness.getSpanTraceId("storefront.checkout.start");
    fetchHeaders.expectTraceparent("/api/orders", traceId);
    fetchHeaders.expectTraceparent("/api/orders/ord-100/reserve", traceId);

    // and
    fetchHeaders.expectNoTraceparent("/otlp/v1/traces");
    harness.expectNoSpansMatchingUrl("/otlp/");
  });
});
