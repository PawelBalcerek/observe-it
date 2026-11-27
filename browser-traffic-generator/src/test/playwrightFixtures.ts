import type { BrowserContextOptions } from "playwright-core";
import type {
  BrowserLauncher,
  ManagedBrowser,
  ManagedBrowserContext,
} from "../browserLifecycle.js";
import type { JourneyLocator, JourneyPage, LocatorRole } from "../journeys.js";

interface RecordedAction {
  readonly type: "goto" | "click" | "check" | "waitFor";
  readonly target: string;
}

interface FakePageHandle {
  readonly page: JourneyPage;
  readonly actions: RecordedAction[];
  readonly defaultTimeouts: number[];
}

interface FakeContextRecord {
  readonly options: BrowserContextOptions | undefined;
  readonly closed: boolean;
  readonly pageHandle: FakePageHandle;
}

interface FakeBrowserRecord {
  connected: boolean;
  closed: boolean;
  failNextContextClose: boolean;
  readonly contexts: FakeContextRecord[];
}

interface FakePlaywrightHarness {
  readonly launcher: BrowserLauncher;
  readonly browsers: FakeBrowserRecord[];
}

const formatLocatorTarget = (role: LocatorRole, name?: string | RegExp): string =>
  name === undefined ? role : `${role}:${String(name)}`;

export const createFakePage = (): FakePageHandle => {
  const actions: RecordedAction[] = [];
  const defaultTimeouts: number[] = [];

  const createLocator = (target: string): JourneyLocator => ({
    click: async (): Promise<void> => {
      actions.push({ type: "click", target });
    },
    check: async (): Promise<void> => {
      actions.push({ type: "check", target });
    },
    waitFor: async (): Promise<void> => {
      actions.push({ type: "waitFor", target });
    },
  });

  const page: JourneyPage = {
    goto: async (url: string): Promise<unknown> => {
      actions.push({ type: "goto", target: url });
      return null;
    },
    getByRole: (role: LocatorRole, options?: { name?: string | RegExp }): JourneyLocator =>
      createLocator(formatLocatorTarget(role, options?.name)),
    getByText: (text: string | RegExp): JourneyLocator => createLocator(`text:${String(text)}`),
    setDefaultTimeout: (timeout: number): void => {
      defaultTimeouts.push(timeout);
    },
  };

  return { page, actions, defaultTimeouts };
};

export const createFakePlaywrightHarness = (): FakePlaywrightHarness => {
  const browsers: FakeBrowserRecord[] = [];

  const launcher: BrowserLauncher = {
    launch: async (): Promise<ManagedBrowser> => {
      const record: FakeBrowserRecord = {
        connected: true,
        closed: false,
        failNextContextClose: false,
        contexts: [],
      };
      browsers.push(record);

      const browser: ManagedBrowser = {
        isConnected: (): boolean => record.connected,
        newContext: async (options?: BrowserContextOptions): Promise<ManagedBrowserContext> => {
          const pageHandle = createFakePage();
          let isClosed = false;
          const contextRecord: FakeContextRecord = {
            options,
            get closed(): boolean {
              return isClosed;
            },
            pageHandle,
          };
          record.contexts.push(contextRecord);

          return {
            newPage: async (): Promise<JourneyPage> => pageHandle.page,
            close: async (): Promise<void> => {
              if (record.failNextContextClose) {
                record.failNextContextClose = false;
                throw new Error("Target page, context or browser has been closed");
              }
              isClosed = true;
            },
          };
        },
        close: async (): Promise<void> => {
          record.connected = false;
          record.closed = true;
        },
      };

      return browser;
    },
  };

  return { launcher, browsers };
};
