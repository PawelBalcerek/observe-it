import type { AddressInfo } from "node:net";
import { afterEach, describe, expect, it } from "vitest";
import { createControlServer } from "./controlServer.js";
import { createFakeTrafficController } from "./test/runnerFixtures.js";

interface JsonHttpResponse {
  readonly status: number;
  readonly body: Record<string, unknown>;
}

const activeServers: Array<{ close: () => Promise<void> }> = [];

const startTestServer = async (
  initialPaused = false,
): Promise<{
  readonly baseUrl: string;
  readonly handle: ReturnType<typeof createFakeTrafficController>;
}> => {
  const handle = createFakeTrafficController(initialPaused);
  const server = createControlServer(handle.controller);

  await new Promise<void>((resolve) => {
    server.listen(0, "127.0.0.1", resolve);
  });

  const address = server.address() as AddressInfo;
  activeServers.push({
    close: async (): Promise<void> => {
      await new Promise<void>((resolve, reject) => {
        server.close((error) => (error ? reject(error) : resolve()));
      });
    },
  });

  return {
    baseUrl: `http://127.0.0.1:${address.port}`,
    handle,
  };
};

const requestJson = async (url: string, method: "GET" | "POST"): Promise<JsonHttpResponse> => {
  const response = await fetch(url, { method });
  const body = (await response.json()) as Record<string, unknown>;
  return { status: response.status, body };
};

const expectResponse = (
  response: JsonHttpResponse,
  expectedStatus: number,
  expectedBody: Record<string, unknown>,
): void => {
  expect(response.status).toBe(expectedStatus);
  expect(response.body).toMatchObject(expectedBody);
};

afterEach(async () => {
  while (activeServers.length > 0) {
    const server = activeServers.pop();
    if (server) {
      await server.close();
    }
  }
});

describe("createControlServer", () => {
  it("returns 200 UP on GET /health", async () => {
    // given
    const { baseUrl } = await startTestServer();

    // when
    const response = await requestJson(`${baseUrl}/health`, "GET");

    // then
    expectResponse(response, 200, { status: "UP" });
  });

  it("returns runner state on GET /status and toggles via POST /pause and POST /resume", async () => {
    // given
    const { baseUrl } = await startTestServer(false);

    // when
    const initial = await requestJson(`${baseUrl}/status`, "GET");
    const paused = await requestJson(`${baseUrl}/pause`, "POST");
    const resumed = await requestJson(`${baseUrl}/resume`, "POST");

    // then
    expectResponse(initial, 200, { running: true, paused: false });
    expectResponse(paused, 200, { running: false, paused: true });
    expectResponse(resumed, 200, { running: true, paused: false });
  });

  it("executes known scenarios synchronously on POST /trigger/{scenario}", async () => {
    // given
    const { baseUrl, handle } = await startTestServer();

    // when
    const response = await requestJson(`${baseUrl}/trigger/BROWSER_DIAGNOSTICS`, "POST");

    // then
    expectResponse(response, 200, { scenario: "BROWSER_DIAGNOSTICS", status: "SUCCESS" });
    expect(handle.triggeredScenarios).toEqual(["BROWSER_DIAGNOSTICS"]);
  });

  it("returns 502 with error payload when a triggered scenario fails", async () => {
    // given
    const { baseUrl, handle } = await startTestServer();
    handle.setTriggerResult({
      scenario: "HAPPY_PATH",
      status: "FAILED",
      durationMs: 120,
      finishedAt: "2026-11-27T20:00:00.000Z",
      error: "Navigation timeout",
    });

    // when
    const response = await requestJson(`${baseUrl}/trigger/HAPPY_PATH`, "POST");

    // then
    expectResponse(response, 502, { status: "FAILED", error: "Navigation timeout" });
  });

  it("returns 400 JSON error on POST /trigger/{scenario} with an unknown or malformed scenario", async () => {
    // given
    const { baseUrl, handle } = await startTestServer();

    // when
    const unknown = await requestJson(`${baseUrl}/trigger/UNKNOWN_SCENARIO`, "POST");
    const malformed = await requestJson(`${baseUrl}/trigger/%E0%A4%A`, "POST");

    // then
    expectResponse(unknown, 400, { error: "Unknown scenario: UNKNOWN_SCENARIO" });
    expectResponse(malformed, 400, { error: "Unknown scenario: %E0%A4%A" });
    expect(handle.triggeredScenarios).toEqual([]);
  });

  it("returns 404 for unknown paths and 405 for unsupported HTTP methods", async () => {
    // given
    const { baseUrl } = await startTestServer();

    // when
    const notFound = await requestJson(`${baseUrl}/unknown`, "GET");
    const methodNotAllowed = await requestJson(`${baseUrl}/health`, "POST");

    // then
    expectResponse(notFound, 404, { error: "Not found" });
    expectResponse(methodNotAllowed, 405, { error: "Method not allowed" });
  });
});
