interface BrowserTrafficConfig {
  readonly storefrontBaseUrl: string;
  readonly port: number;
  readonly autoStart: boolean;
  readonly minDelayMs: number;
  readonly maxDelayMs: number;
  readonly recycleAfterJourneys: number;
  readonly actionTimeoutMs: number;
}

const DEFAULT_STOREFRONT_BASE_URL = "http://storefront:8080";
const DEFAULT_PORT = 8082;
const DEFAULT_AUTO_START = true;
const DEFAULT_MIN_DELAY_MS = 5000;
const DEFAULT_MAX_DELAY_MS = 10000;
const DEFAULT_RECYCLE_AFTER_JOURNEYS = 75;
const DEFAULT_ACTION_TIMEOUT_MS = 15000;

const parseBoundedInt = (
  raw: string | undefined,
  fallback: number,
  minValue: number,
  name: string,
): number => {
  if (raw === undefined || raw.trim() === "") {
    return fallback;
  }
  const parsed = Number.parseInt(raw, 10);
  if (!Number.isFinite(parsed) || parsed < minValue) {
    throw new Error(`${name} must be an integer >= ${minValue}, got "${raw}"`);
  }
  return parsed;
};

const parseBoolean = (raw: string | undefined, fallback: boolean): boolean => {
  if (raw === undefined || raw.trim() === "") {
    return fallback;
  }
  return raw.trim().toLowerCase() !== "false";
};

export const loadConfig = (env: NodeJS.ProcessEnv): BrowserTrafficConfig => {
  const rawUrl = env.STOREFRONT_BASE_URL?.trim();
  const storefrontBaseUrl = (
    rawUrl && rawUrl.length > 0 ? rawUrl : DEFAULT_STOREFRONT_BASE_URL
  ).replace(/\/+$/, "");

  const port = parseBoundedInt(env.PORT, DEFAULT_PORT, 1, "PORT");
  const autoStart = parseBoolean(env.BROWSER_TRAFFIC_AUTO_START, DEFAULT_AUTO_START);
  const minDelayMs = parseBoundedInt(
    env.BROWSER_TRAFFIC_MIN_DELAY_MS,
    DEFAULT_MIN_DELAY_MS,
    0,
    "BROWSER_TRAFFIC_MIN_DELAY_MS",
  );
  const maxDelayMs = parseBoundedInt(
    env.BROWSER_TRAFFIC_MAX_DELAY_MS,
    DEFAULT_MAX_DELAY_MS,
    0,
    "BROWSER_TRAFFIC_MAX_DELAY_MS",
  );

  if (maxDelayMs < minDelayMs) {
    throw new Error(
      "BROWSER_TRAFFIC_MAX_DELAY_MS must be greater than or equal to BROWSER_TRAFFIC_MIN_DELAY_MS",
    );
  }

  const recycleAfterJourneys = parseBoundedInt(
    env.BROWSER_TRAFFIC_RECYCLE_AFTER_JOURNEYS,
    DEFAULT_RECYCLE_AFTER_JOURNEYS,
    1,
    "BROWSER_TRAFFIC_RECYCLE_AFTER_JOURNEYS",
  );
  const actionTimeoutMs = parseBoundedInt(
    env.BROWSER_TRAFFIC_ACTION_TIMEOUT_MS,
    DEFAULT_ACTION_TIMEOUT_MS,
    1,
    "BROWSER_TRAFFIC_ACTION_TIMEOUT_MS",
  );

  return {
    storefrontBaseUrl,
    port,
    autoStart,
    minDelayMs,
    maxDelayMs,
    recycleAfterJourneys,
    actionTimeoutMs,
  };
};
