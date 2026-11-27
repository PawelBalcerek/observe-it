import { describe, expect, it } from "vitest";
import { createBrowserSessionManager } from "./browserLifecycle.js";
import { createFakePlaywrightHarness } from "./test/playwrightFixtures.js";

const createTestSession = (overrides?: {
  readonly recycleAfterJourneys?: number;
  readonly actionTimeoutMs?: number;
}): {
  readonly harness: ReturnType<typeof createFakePlaywrightHarness>;
  readonly manager: ReturnType<typeof createBrowserSessionManager>;
} => {
  const harness = createFakePlaywrightHarness();
  const manager = createBrowserSessionManager({
    launcher: harness.launcher,
    recycleAfterJourneys: overrides?.recycleAfterJourneys ?? 50,
    actionTimeoutMs: overrides?.actionTimeoutMs ?? 15000,
  });
  return { harness, manager };
};

const expectBrowserSnapshots = (
  harness: ReturnType<typeof createFakePlaywrightHarness>,
  expected: ReadonlyArray<{ readonly closed: boolean; readonly contextCount: number }>,
): void => {
  const actual = harness.browsers.map((browser) => ({
    closed: browser.closed,
    contextCount: browser.contexts.length,
  }));
  expect(actual).toEqual(expected);
};

const expectContextSnapshots = (
  harness: ReturnType<typeof createFakePlaywrightHarness>,
  expected: ReadonlyArray<{
    readonly scenarioHeader: string | undefined;
    readonly closed: boolean;
    readonly timeoutMs: number;
  }>,
): void => {
  const actual = harness.browsers.flatMap((browser) =>
    browser.contexts.map((context) => ({
      scenarioHeader: context.options?.extraHTTPHeaders?.["X-Checkout-Scenario"],
      closed: context.closed,
      timeoutMs: context.pageHandle.defaultTimeouts[0],
    })),
  );
  expect(actual).toEqual(expected);
};

describe("createBrowserSessionManager", () => {
  it("attaches X-Checkout-Scenario header for checkout scenarios and omits it for BROWSER_DIAGNOSTICS", async () => {
    // given
    const { harness, manager } = createTestSession({
      recycleAfterJourneys: 10,
      actionTimeoutMs: 12000,
    });

    // when
    await manager.runInFreshContext("HAPPY_PATH", async () => {});
    await manager.runInFreshContext("BROWSER_DIAGNOSTICS", async () => {});

    // then
    expectBrowserSnapshots(harness, [{ closed: false, contextCount: 2 }]);
    expectContextSnapshots(harness, [
      { scenarioHeader: "HAPPY_PATH", closed: true, timeoutMs: 12000 },
      { scenarioHeader: undefined, closed: true, timeoutMs: 12000 },
    ]);
  });

  it("closes BrowserContext in finally even when the journey action throws", async () => {
    // given
    const { harness, manager } = createTestSession({ recycleAfterJourneys: 10 });

    // when
    await expect(
      manager.runInFreshContext("PAYMENT_DECLINED", async () => {
        throw new Error("Simulated timeout");
      }),
    ).rejects.toThrow("Simulated timeout");

    // then
    expectContextSnapshots(harness, [
      { scenarioHeader: "PAYMENT_DECLINED", closed: true, timeoutMs: 15000 },
    ]);
  });

  it("recycles the shared Browser instance after recycleAfterJourneys journeys", async () => {
    // given
    const { harness, manager } = createTestSession({ recycleAfterJourneys: 2 });

    // when
    await manager.runInFreshContext("HAPPY_PATH", async () => {});
    await manager.runInFreshContext("OUT_OF_STOCK", async () => {});
    await manager.runInFreshContext("BUYER_CANCELLATION", async () => {});

    // then
    expectBrowserSnapshots(harness, [
      { closed: true, contextCount: 2 },
      { closed: false, contextCount: 1 },
    ]);
  });

  it("relaunches the Browser when the existing instance becomes disconnected", async () => {
    // given
    const { harness, manager } = createTestSession();
    await manager.runInFreshContext("HAPPY_PATH", async () => {});
    if (harness.browsers[0]) {
      harness.browsers[0].connected = false;
    }

    // when
    await manager.runInFreshContext("LOW_STOCK_WARNING", async () => {});

    // then
    expectBrowserSnapshots(harness, [
      { closed: false, contextCount: 1 },
      { closed: false, contextCount: 1 },
    ]);
  });

  it("closes the active Browser on shutdown", async () => {
    // given
    const { harness, manager } = createTestSession();
    await manager.runInFreshContext("HAPPY_PATH", async () => {});

    // when
    await manager.close();

    // then
    expectBrowserSnapshots(harness, [{ closed: true, contextCount: 1 }]);
  });

  it("preserves the journey error and recycles the Browser when context.close rejects", async () => {
    // given
    const { harness, manager } = createTestSession();

    // when
    await expect(
      manager.runInFreshContext("HAPPY_PATH", async () => {
        if (harness.browsers[0]) {
          harness.browsers[0].failNextContextClose = true;
        }
        throw new Error("Primary navigation failure");
      }),
    ).rejects.toThrow("Primary navigation failure");

    // then
    expectBrowserSnapshots(harness, [{ closed: true, contextCount: 1 }]);
  });
});
