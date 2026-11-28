import type {
  Order,
  OrderLineItem,
  OrderTransitionResponse,
  PaymentMethod,
  ProblemDetail,
  ShippingMethod,
} from "./types.ts";

const HTTP_METHOD = {
  GET: "GET",
  POST: "POST",
} as const;

const HTTP_HEADER = {
  CONTENT_TYPE: "Content-Type",
} as const;

const CONTENT_TYPE_JSON = "application/json";
const ORDERS_BASE_PATH = "/api/orders";

const orderPath = (orderId: string, action?: string): string => {
  const encodedId = encodeURIComponent(orderId);
  return action ? `${ORDERS_BASE_PATH}/${encodedId}/${action}` : `${ORDERS_BASE_PATH}/${encodedId}`;
};

export class ApiError extends Error {
  readonly status: number;
  readonly detail: string;

  constructor(status: number, detail: string) {
    super(detail);
    this.name = "ApiError";
    this.status = status;
    this.detail = detail;
  }
}

const parseProblemDetail = async (response: Response): Promise<ProblemDetail | undefined> => {
  try {
    const data: unknown = await response.json();
    if (typeof data === "object" && data !== null) {
      return data as ProblemDetail;
    }
  } catch {
    // Response body is not JSON; fall back to statusText.
  }
  return undefined;
};

const requestJson = async <T>(url: string, init?: RequestInit): Promise<T> => {
  const headers = new Headers(init?.headers);
  if (init?.body !== undefined && !headers.has(HTTP_HEADER.CONTENT_TYPE)) {
    headers.set(HTTP_HEADER.CONTENT_TYPE, CONTENT_TYPE_JSON);
  }

  const response = await fetch(url, {
    ...init,
    headers,
  });

  if (!response.ok) {
    const problem = await parseProblemDetail(response);
    const detail =
      problem?.detail ??
      problem?.title ??
      (response.statusText || `Request failed with status ${response.status}`);
    throw new ApiError(response.status, detail);
  }

  return (await response.json()) as T;
};

export const createOrder = (
  customerId: string,
  items: readonly OrderLineItem[],
): Promise<OrderTransitionResponse> =>
  requestJson<OrderTransitionResponse>(ORDERS_BASE_PATH, {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({ customerId, items }),
  });

export const reserveOrder = (orderId: string): Promise<OrderTransitionResponse> =>
  requestJson<OrderTransitionResponse>(orderPath(orderId, "reserve"), {
    method: HTTP_METHOD.POST,
  });

export const payOrder = (
  orderId: string,
  paymentMethod: PaymentMethod,
): Promise<OrderTransitionResponse> =>
  requestJson<OrderTransitionResponse>(orderPath(orderId, "pay"), {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({ paymentMethod }),
  });

export const shipOrder = (
  orderId: string,
  shippingMethod: ShippingMethod,
): Promise<OrderTransitionResponse> =>
  requestJson<OrderTransitionResponse>(orderPath(orderId, "ship"), {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({ shippingMethod }),
  });

export const cancelOrder = (orderId: string, reason: string): Promise<OrderTransitionResponse> =>
  requestJson<OrderTransitionResponse>(orderPath(orderId, "cancel"), {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({ reason }),
  });

export const getOrder = (orderId: string): Promise<Order> =>
  requestJson<Order>(orderPath(orderId), {
    method: HTTP_METHOD.GET,
  });
