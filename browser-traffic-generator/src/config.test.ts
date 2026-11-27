import { describe, expect, it } from "vitest";
import { loadConfig } from "./config.js";

describe("loadConfig", () => {
  it("parses custom environment variables and trims trailing slashes on base URL", () => {
    // given
    const env: NodeJS.ProcessEnv = {
      STOREFRONT_BASE_URL: "http://localhost:5173///",
      PORT: "9090",
      BROWSER_TRAFFIC_AUTO_START: "false",
      BROWSER_TRAFFIC_MIN_DELAY_MS: "2000",
      BROWSER_TRAFFIC_MAX_DELAY_MS: "4000",
      BROWSER_TRAFFIC_RECYCLE_AFTER_JOURNEYS: "50",
      BROWSER_TRAFFIC_ACTION_TIMEOUT_MS: "8000",
    };

    // when
    const config = loadConfig(env);

    // then
    expect(config).toEqual({
      storefrontBaseUrl: "http://localhost:5173",
      port: 9090,
      autoStart: false,
      minDelayMs: 2000,
      maxDelayMs: 4000,
      recycleAfterJourneys: 50,
      actionTimeoutMs: 8000,
    });
  });

  it("rejects invalid min/max delay ranges", () => {
    // given
    const env: NodeJS.ProcessEnv = {
      BROWSER_TRAFFIC_MIN_DELAY_MS: "12000",
      BROWSER_TRAFFIC_MAX_DELAY_MS: "5000",
    };

    // when
    const load = (): ReturnType<typeof loadConfig> => loadConfig(env);

    // then
    expect(load).toThrow(
      "BROWSER_TRAFFIC_MAX_DELAY_MS must be greater than or equal to BROWSER_TRAFFIC_MIN_DELAY_MS",
    );
  });
});
