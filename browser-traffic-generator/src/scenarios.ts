export const SCENARIO_NAMES = [
  "HAPPY_PATH",
  "LOW_STOCK_WARNING",
  "OUT_OF_STOCK",
  "PAYMENT_DECLINED",
  "PAYMENT_GATEWAY_TIMEOUT",
  "BUYER_CANCELLATION",
  "BROWSER_DIAGNOSTICS",
] as const;

export type ScenarioName = (typeof SCENARIO_NAMES)[number];

const SCENARIO_WEIGHTS: Readonly<Record<ScenarioName, number>> = {
  HAPPY_PATH: 52,
  LOW_STOCK_WARNING: 15,
  OUT_OF_STOCK: 10,
  PAYMENT_DECLINED: 8,
  PAYMENT_GATEWAY_TIMEOUT: 5,
  BUYER_CANCELLATION: 6,
  BROWSER_DIAGNOSTICS: 4,
};

const TOTAL_WEIGHT = SCENARIO_NAMES.reduce(
  (sum: number, scenario: ScenarioName): number => sum + SCENARIO_WEIGHTS[scenario],
  0,
);

const SCENARIO_SET: ReadonlySet<string> = new Set(SCENARIO_NAMES);

export const isScenarioName = (value: string): value is ScenarioName => SCENARIO_SET.has(value);

export const selectWeightedScenario = (randomValue: number): ScenarioName => {
  const clamped = Math.min(Math.max(randomValue, 0), 1 - Number.EPSILON);
  const target = clamped * TOTAL_WEIGHT;
  let cumulative = 0;

  for (const scenario of SCENARIO_NAMES) {
    cumulative += SCENARIO_WEIGHTS[scenario];
    if (target < cumulative) {
      return scenario;
    }
  }

  return SCENARIO_NAMES[SCENARIO_NAMES.length - 1];
};

export const createEmptyScenarioCounts = (): Record<ScenarioName, number> => ({
  HAPPY_PATH: 0,
  LOW_STOCK_WARNING: 0,
  OUT_OF_STOCK: 0,
  PAYMENT_DECLINED: 0,
  PAYMENT_GATEWAY_TIMEOUT: 0,
  BUYER_CANCELLATION: 0,
  BROWSER_DIAGNOSTICS: 0,
});

export const scenarioHeaderValue = (scenario: ScenarioName): string | undefined =>
  scenario === "BROWSER_DIAGNOSTICS" ? undefined : scenario;
