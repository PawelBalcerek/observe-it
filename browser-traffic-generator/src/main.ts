import { chromium } from "playwright-core";
import { createBrowserSessionManager, type ManagedBrowser } from "./browserLifecycle.js";
import { loadConfig } from "./config.js";
import { createControlServer } from "./controlServer.js";
import { executeScenarioJourney } from "./journeys.js";
import type { ScenarioName } from "./scenarios.js";
import { createTrafficRunner, type JourneyOutcome } from "./trafficRunner.js";

const config = loadConfig(process.env);

const sessionManager = createBrowserSessionManager({
  launcher: {
    launch: async (): Promise<ManagedBrowser> => chromium.launch({ headless: true }),
  },
  recycleAfterJourneys: config.recycleAfterJourneys,
  actionTimeoutMs: config.actionTimeoutMs,
});

const runner = createTrafficRunner({
  autoStart: config.autoStart,
  minDelayMs: config.minDelayMs,
  maxDelayMs: config.maxDelayMs,
  executeScenario: async (scenario: ScenarioName): Promise<void> => {
    await sessionManager.runInFreshContext(scenario, async (page): Promise<void> => {
      await executeScenarioJourney(page, config.storefrontBaseUrl, scenario);
    });
  },
  closeBrowser: (): Promise<void> => sessionManager.close(),
  onJourneyCompleted: (outcome: JourneyOutcome): void => {
    if (outcome.status === "FAILED") {
      console.error(
        `[browser-traffic-generator] scenario=${outcome.scenario} status=FAILED durationMs=${outcome.durationMs} error=${outcome.error ?? "unknown"}`,
      );
    } else {
      console.info(
        `[browser-traffic-generator] scenario=${outcome.scenario} status=SUCCESS durationMs=${outcome.durationMs}`,
      );
    }
  },
});

const server = createControlServer(runner);

server.listen(config.port, "0.0.0.0", (): void => {
  console.info(
    `[browser-traffic-generator] control server listening on :${config.port}, target=${config.storefrontBaseUrl}, autoStart=${String(config.autoStart)}`,
  );
  runner.start();
});

let shuttingDown = false;
const shutdown = (signal: string): void => {
  if (shuttingDown) {
    return;
  }
  shuttingDown = true;
  console.info(`[browser-traffic-generator] received ${signal}, shutting down`);
  server.close((): void => {
    void runner.stop().finally((): void => {
      process.exit(0);
    });
  });
};

process.on("SIGINT", (): void => shutdown("SIGINT"));
process.on("SIGTERM", (): void => shutdown("SIGTERM"));
