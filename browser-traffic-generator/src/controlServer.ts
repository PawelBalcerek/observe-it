import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { isScenarioName, SCENARIO_NAMES } from "./scenarios.js";
import type { TrafficController } from "./trafficRunner.js";

const HTTP_METHOD = {
  GET: "GET",
  POST: "POST",
} as const;

type HttpMethod = (typeof HTTP_METHOD)[keyof typeof HTTP_METHOD];

const HTTP_STATUS = {
  OK: 200,
  BAD_REQUEST: 400,
  NOT_FOUND: 404,
  METHOD_NOT_ALLOWED: 405,
  INTERNAL_SERVER_ERROR: 500,
  BAD_GATEWAY: 502,
} as const;

const HTTP_HEADER = {
  CONTENT_TYPE: "Content-Type",
  CONTENT_LENGTH: "Content-Length",
} as const;

const CONTENT_TYPE_JSON = "application/json; charset=utf-8";

const ROUTES = {
  HEALTH: "/health",
  STATUS: "/status",
  PAUSE: "/pause",
  RESUME: "/resume",
  TRIGGER_PREFIX: "/trigger/",
} as const;

const sendJson = (res: ServerResponse, statusCode: number, payload: unknown): void => {
  const body = JSON.stringify(payload);
  res.writeHead(statusCode, {
    [HTTP_HEADER.CONTENT_TYPE]: CONTENT_TYPE_JSON,
    [HTTP_HEADER.CONTENT_LENGTH]: Buffer.byteLength(body),
  });
  res.end(body);
};

const parsePathname = (req: IncomingMessage): string => {
  const url = new URL(req.url ?? "/", "http://localhost");
  return url.pathname;
};

const requireMethod = (
  req: IncomingMessage,
  res: ServerResponse,
  expected: HttpMethod,
): boolean => {
  if ((req.method ?? HTTP_METHOD.GET) !== expected) {
    sendJson(res, HTTP_STATUS.METHOD_NOT_ALLOWED, { error: "Method not allowed" });
    return false;
  }
  return true;
};

const decodeScenarioParam = (rawSegment: string): string | null => {
  try {
    return decodeURIComponent(rawSegment);
  } catch {
    return null;
  }
};

const handleRequest = async (
  controller: TrafficController,
  req: IncomingMessage,
  res: ServerResponse,
): Promise<void> => {
  const pathname = parsePathname(req);

  if (pathname === ROUTES.HEALTH) {
    if (requireMethod(req, res, HTTP_METHOD.GET)) {
      sendJson(res, HTTP_STATUS.OK, { status: "UP" });
    }
    return;
  }

  if (pathname === ROUTES.STATUS) {
    if (requireMethod(req, res, HTTP_METHOD.GET)) {
      sendJson(res, HTTP_STATUS.OK, controller.getStatus());
    }
    return;
  }

  if (pathname === ROUTES.PAUSE) {
    if (requireMethod(req, res, HTTP_METHOD.POST)) {
      sendJson(res, HTTP_STATUS.OK, controller.pause());
    }
    return;
  }

  if (pathname === ROUTES.RESUME) {
    if (requireMethod(req, res, HTTP_METHOD.POST)) {
      sendJson(res, HTTP_STATUS.OK, controller.resume());
    }
    return;
  }

  if (pathname.startsWith(ROUTES.TRIGGER_PREFIX)) {
    if (!requireMethod(req, res, HTTP_METHOD.POST)) {
      return;
    }

    const rawSegment = pathname.slice(ROUTES.TRIGGER_PREFIX.length);
    const rawScenario = decodeScenarioParam(rawSegment);
    if (rawScenario === null || !isScenarioName(rawScenario)) {
      sendJson(res, HTTP_STATUS.BAD_REQUEST, {
        error: `Unknown scenario: ${rawScenario ?? rawSegment}`,
        allowedScenarios: SCENARIO_NAMES,
      });
      return;
    }

    const outcome = await controller.trigger(rawScenario);
    const statusCode = outcome.status === "SUCCESS" ? HTTP_STATUS.OK : HTTP_STATUS.BAD_GATEWAY;
    sendJson(res, statusCode, outcome);
    return;
  }

  sendJson(res, HTTP_STATUS.NOT_FOUND, { error: "Not found" });
};

export const createControlServer = (controller: TrafficController): Server =>
  createServer((req: IncomingMessage, res: ServerResponse): void => {
    void handleRequest(controller, req, res).catch((error: unknown): void => {
      const message = error instanceof Error ? error.message : String(error);
      if (!res.headersSent) {
        sendJson(res, HTTP_STATUS.INTERNAL_SERVER_ERROR, { error: message });
      }
    });
  });
