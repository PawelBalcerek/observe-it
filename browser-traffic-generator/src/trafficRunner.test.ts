import { afterEach, describe, expect, it, vi } from "vitest";
import type { ScenarioName } from "./scenarios.js";
import { createTrafficRunner, type TrafficRunnerStatus } from "./trafficRunner.js";

const createTestRunner = (overrides?: {
  readonly autoStart?: boolean;
  readonly delayMs?: number;
  readonly random?: () => number;
  readonly executeScenario?: (scenario: ScenarioName) => Promise<void>;
}): {
  readonly runner: ReturnType<typeof createTrafficRunner>;
  readonly executed: ScenarioName[];
  readonly isBrowserClosed: () => boolean;
} => {
  const executed: ScenarioName[] = [];
  let browserClosed = false;
  const delayMs = overrides?.delayMs ?? 5000;

  const runner = createTrafficRunner({
    autoStart: overrides?.autoStart ?? false,
    minDelayMs: delayMs,
    maxDelayMs: delayMs,
    random: overrides?.random,
    executeScenario: async (scenario: ScenarioName): Promise<void> => {
      executed.push(scenario);
      await overrides?.executeScenario?.(scenario);
    },
    closeBrowser: async (): Promise<void> => {
      browserClosed = true;
    },
  });

  return { runner, executed, isBrowserClosed: (): boolean => browserClosed };
};

const expectStatus = (
  status: TrafficRunnerStatus,
  expected: Partial<TrafficRunnerStatus>,
): void => {
  expect(status).toMatchObject(expected);
};

afterEach(() => {
  vi.useRealTimers();
});

describe("createTrafficRunner", () => {
  it("executes triggered scenarios, updates counts, and records last outcome", async () => {
    // given
    const { runner, executed } = createTestRunner();

    // when
    const outcome = await runner.trigger("HAPPY_PATH");

    // then
    expect(executed).toEqual(["HAPPY_PATH"]);
    expect(outcome).toMatchObject({ scenario: "HAPPY_PATH", status: "SUCCESS" });
    expectStatus(runner.getStatus(), {
      running: false,
      paused: true,
      totalJourneys: 1,
      lastOutcome: outcome,
      lastError: null,
    });
    expect(runner.getStatus().countsByScenario.HAPPY_PATH).toBe(1);
  });

  it("records FAILED outcome and lastError when a scenario throws", async () => {
    // given
    const { runner } = createTestRunner({
      executeScenario: async (): Promise<void> => {
        throw new Error("Storefront unreachable");
      },
    });

    // when
    const outcome = await runner.trigger("PAYMENT_GATEWAY_TIMEOUT");

    // then
    expect(outcome).toMatchObject({
      scenario: "PAYMENT_GATEWAY_TIMEOUT",
      status: "FAILED",
      error: "Storefront unreachable",
    });
    expectStatus(runner.getStatus(), {
      totalJourneys: 1,
      lastError: "Storefront unreachable",
    });
    expect(runner.getStatus().countsByScenario.PAYMENT_GATEWAY_TIMEOUT).toBe(1);
  });

  it("toggles steady-state loop with pause and resume and runs weighted journeys when active", async () => {
    // given
    vi.useFakeTimers();
    const { runner, executed, isBrowserClosed } = createTestRunner({
      autoStart: true,
      random: (): number => 0.97,
      executeScenario: async (): Promise<void> => {
        runner.pause();
      },
    });

    // when
    runner.start();
    await vi.advanceTimersByTimeAsync(5000);
    const pausedStatus = runner.getStatus();
    const resumedStatus = runner.resume();
    await runner.stop();

    // then
    expect(executed).toEqual(["BROWSER_DIAGNOSTICS"]);
    expectStatus(pausedStatus, { running: false, paused: true });
    expectStatus(resumedStatus, { running: true, paused: false });
    expect(isBrowserClosed()).toBe(true);
  });
});
