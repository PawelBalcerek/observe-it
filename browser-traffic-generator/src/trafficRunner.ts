import {
  createEmptyScenarioCounts,
  type ScenarioName,
  selectWeightedScenario,
} from "./scenarios.js";

export interface JourneyOutcome {
  readonly scenario: ScenarioName;
  readonly status: "SUCCESS" | "FAILED";
  readonly durationMs: number;
  readonly finishedAt: string;
  readonly error?: string;
}

export interface TrafficRunnerStatus {
  readonly running: boolean;
  readonly paused: boolean;
  readonly inFlight: boolean;
  readonly totalJourneys: number;
  readonly countsByScenario: Readonly<Record<ScenarioName, number>>;
  readonly lastOutcome: JourneyOutcome | null;
  readonly lastError: string | null;
}

export interface TrafficController {
  getStatus(): TrafficRunnerStatus;
  pause(): TrafficRunnerStatus;
  resume(): TrafficRunnerStatus;
  trigger(scenario: ScenarioName): Promise<JourneyOutcome>;
}

interface TrafficRunner extends TrafficController {
  start(): void;
  stop(): Promise<void>;
}

interface TrafficRunnerOptions {
  readonly autoStart: boolean;
  readonly minDelayMs: number;
  readonly maxDelayMs: number;
  readonly executeScenario: (scenario: ScenarioName) => Promise<void>;
  readonly closeBrowser: () => Promise<void>;
  readonly random?: () => number;
  readonly onJourneyCompleted?: (outcome: JourneyOutcome) => void;
}

const formatError = (error: unknown): string =>
  error instanceof Error ? error.message : String(error);

export const createTrafficRunner = (options: TrafficRunnerOptions): TrafficRunner => {
  const random = options.random ?? Math.random;
  const countsByScenario = createEmptyScenarioCounts();

  let paused = !options.autoStart;
  let stopped = false;
  let inFlight = false;
  let totalJourneys = 0;
  let lastOutcome: JourneyOutcome | null = null;
  let lastError: string | null = null;
  let pendingTimer: ReturnType<typeof setTimeout> | null = null;
  let executionChain: Promise<void> = Promise.resolve();

  const clearPendingTimer = (): void => {
    if (pendingTimer !== null) {
      clearTimeout(pendingTimer);
      pendingTimer = null;
    }
  };

  const getStatus = (): TrafficRunnerStatus => ({
    running: !paused && !stopped,
    paused,
    inFlight,
    totalJourneys,
    countsByScenario: { ...countsByScenario },
    lastOutcome,
    lastError,
  });

  const recordOutcome = (
    scenario: ScenarioName,
    startedAt: number,
    errorMessage?: string,
  ): JourneyOutcome => {
    const outcome: JourneyOutcome =
      errorMessage === undefined
        ? {
            scenario,
            status: "SUCCESS",
            durationMs: Date.now() - startedAt,
            finishedAt: new Date().toISOString(),
          }
        : {
            scenario,
            status: "FAILED",
            durationMs: Date.now() - startedAt,
            finishedAt: new Date().toISOString(),
            error: errorMessage,
          };

    totalJourneys += 1;
    countsByScenario[scenario] += 1;
    lastOutcome = outcome;
    lastError = errorMessage ?? null;
    options.onJourneyCompleted?.(outcome);
    return outcome;
  };

  const runScenarioSerialized = (scenario: ScenarioName): Promise<JourneyOutcome> => {
    const run = async (): Promise<JourneyOutcome> => {
      inFlight = true;
      const startedAt = Date.now();
      try {
        await options.executeScenario(scenario);
        return recordOutcome(scenario, startedAt);
      } catch (error: unknown) {
        return recordOutcome(scenario, startedAt, formatError(error));
      } finally {
        inFlight = false;
      }
    };

    const resultPromise = executionChain.then(run, run);
    executionChain = resultPromise.then(
      () => undefined,
      () => undefined,
    );
    return resultPromise;
  };

  const nextDelayMs = (): number => {
    const span = options.maxDelayMs - options.minDelayMs;
    return options.minDelayMs + Math.round(random() * span);
  };

  const scheduleNextJourney = (): void => {
    clearPendingTimer();
    if (paused || stopped) {
      return;
    }

    pendingTimer = setTimeout(() => {
      pendingTimer = null;
      if (paused || stopped) {
        return;
      }
      const scenario = selectWeightedScenario(random());
      void runScenarioSerialized(scenario).finally(() => {
        if (!paused && !stopped) {
          scheduleNextJourney();
        }
      });
    }, nextDelayMs());
  };

  const start = (): void => {
    stopped = false;
    if (!paused) {
      scheduleNextJourney();
    }
  };

  const pause = (): TrafficRunnerStatus => {
    paused = true;
    clearPendingTimer();
    return getStatus();
  };

  const resume = (): TrafficRunnerStatus => {
    paused = false;
    if (!stopped) {
      scheduleNextJourney();
    }
    return getStatus();
  };

  const trigger = (scenario: ScenarioName): Promise<JourneyOutcome> =>
    runScenarioSerialized(scenario);

  const stop = async (): Promise<void> => {
    stopped = true;
    paused = true;
    clearPendingTimer();
    await executionChain;
    await options.closeBrowser();
  };

  return {
    start,
    pause,
    resume,
    trigger,
    getStatus,
    stop,
  };
};
