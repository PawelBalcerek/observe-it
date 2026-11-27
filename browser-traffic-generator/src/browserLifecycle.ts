import type { BrowserContextOptions } from "playwright-core";
import type { JourneyPage } from "./journeys.js";
import { type ScenarioName, scenarioHeaderValue } from "./scenarios.js";

export interface ManagedBrowserContext {
  newPage(): Promise<JourneyPage>;
  close(): Promise<void>;
}

export interface ManagedBrowser {
  isConnected(): boolean;
  newContext(options?: BrowserContextOptions): Promise<ManagedBrowserContext>;
  close(): Promise<void>;
}

export interface BrowserLauncher {
  launch(): Promise<ManagedBrowser>;
}

interface BrowserSessionManagerOptions {
  readonly launcher: BrowserLauncher;
  readonly recycleAfterJourneys: number;
  readonly actionTimeoutMs: number;
}

interface BrowserSessionManager {
  runInFreshContext<T>(
    scenario: ScenarioName,
    action: (page: JourneyPage) => Promise<T>,
  ): Promise<T>;
  close(): Promise<void>;
}

const HEADER_CHECKOUT_SCENARIO = "X-Checkout-Scenario";

export const createBrowserSessionManager = (
  options: BrowserSessionManagerOptions,
): BrowserSessionManager => {
  let activeBrowser: ManagedBrowser | null = null;
  let journeysOnCurrentBrowser = 0;

  const closeActiveBrowser = async (): Promise<void> => {
    const browserToClose = activeBrowser;
    activeBrowser = null;
    journeysOnCurrentBrowser = 0;
    if (browserToClose !== null) {
      try {
        await browserToClose.close();
      } catch {
        // Ignore close errors when Chromium has already exited or disconnected.
      }
    }
  };

  const ensureBrowser = async (): Promise<ManagedBrowser> => {
    if (activeBrowser === null || !activeBrowser.isConnected()) {
      activeBrowser = await options.launcher.launch();
      journeysOnCurrentBrowser = 0;
    }
    return activeBrowser;
  };

  const runInFreshContext = async <T>(
    scenario: ScenarioName,
    action: (page: JourneyPage) => Promise<T>,
  ): Promise<T> => {
    const browser = await ensureBrowser();
    const headerValue = scenarioHeaderValue(scenario);
    const contextOptions: BrowserContextOptions | undefined =
      headerValue !== undefined
        ? { extraHTTPHeaders: { [HEADER_CHECKOUT_SCENARIO]: headerValue } }
        : undefined;

    const context = await browser.newContext(contextOptions);
    let contextCloseFailed = false;
    try {
      const page = await context.newPage();
      page.setDefaultTimeout(options.actionTimeoutMs);
      return await action(page);
    } finally {
      try {
        await context.close();
      } catch {
        contextCloseFailed = true;
      }
      journeysOnCurrentBrowser += 1;
      if (
        contextCloseFailed ||
        journeysOnCurrentBrowser >= options.recycleAfterJourneys ||
        !browser.isConnected()
      ) {
        await closeActiveBrowser();
      }
    }
  };

  return {
    runInFreshContext,
    close: closeActiveBrowser,
  };
};
