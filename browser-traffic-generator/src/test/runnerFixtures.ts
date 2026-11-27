import { createEmptyScenarioCounts, type ScenarioName } from "../scenarios.js";
import type { JourneyOutcome, TrafficController, TrafficRunnerStatus } from "../trafficRunner.js";

interface FakeTrafficControllerHandle {
  readonly controller: TrafficController;
  readonly triggeredScenarios: ScenarioName[];
  setTriggerResult(result: JourneyOutcome): void;
}

export const createFakeTrafficController = (initialPaused = false): FakeTrafficControllerHandle => {
  let paused = initialPaused;
  let totalJourneys = 0;
  const countsByScenario = createEmptyScenarioCounts();
  let lastOutcome: JourneyOutcome | null = null;
  let lastError: string | null = null;
  const triggeredScenarios: ScenarioName[] = [];
  let nextTriggerResult: JourneyOutcome | null = null;

  const snapshot = (): TrafficRunnerStatus => ({
    running: !paused,
    paused,
    inFlight: false,
    totalJourneys,
    countsByScenario: { ...countsByScenario },
    lastOutcome,
    lastError,
  });

  const controller: TrafficController = {
    getStatus: snapshot,
    pause: (): TrafficRunnerStatus => {
      paused = true;
      return snapshot();
    },
    resume: (): TrafficRunnerStatus => {
      paused = false;
      return snapshot();
    },
    trigger: async (scenario: ScenarioName): Promise<JourneyOutcome> => {
      triggeredScenarios.push(scenario);
      const outcome: JourneyOutcome = nextTriggerResult ?? {
        scenario,
        status: "SUCCESS",
        durationMs: 42,
        finishedAt: "2026-11-27T20:00:00.000Z",
      };
      nextTriggerResult = null;
      totalJourneys += 1;
      countsByScenario[scenario] += 1;
      lastOutcome = outcome;
      lastError = outcome.status === "FAILED" ? (outcome.error ?? "Unknown error") : null;
      return outcome;
    },
  };

  return {
    controller,
    triggeredScenarios,
    setTriggerResult: (result: JourneyOutcome): void => {
      nextTriggerResult = result;
    },
  };
};
