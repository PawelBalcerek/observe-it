import type {
  Order,
  OrderLineItem,
  OrderTransitionResponse,
  PaymentMethod,
  ProblemDetail,
  ShippingMethod,
} from "./types.ts";

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
  if (init?.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
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
  requestJson<OrderTransitionResponse>("/api/orders", {
    method: "POST",
    body: JSON.stringify({ customerId, items }),
  });

export const reserveOrder = (orderId: string): Promise<OrderTransitionResponse> =>
  requestJson<OrderTransitionResponse>(`/api/orders/${encodeURIComponent(orderId)}/reserve`, {
    method: "POST",
  });

export const payOrder = (
  orderId: string,
  paymentMethod: PaymentMethod,
): Promise<OrderTransitionResponse> =>
  requestJson<OrderTransitionResponse>(`/api/orders/${encodeURIComponent(orderId)}/pay`, {
    method: "POST",
    body: JSON.stringify({ paymentMethod }),
  });

export const shipOrder = (
  orderId: string,
  shippingMethod: ShippingMethod,
): Promise<OrderTransitionResponse> =>
  requestJson<OrderTransitionResponse>(`/api/orders/${encodeURIComponent(orderId)}/ship`, {
    method: "POST",
    body: JSON.stringify({ shippingMethod }),
  });

export const cancelOrder = (orderId: string, reason: string): Promise<OrderTransitionResponse> =>
  requestJson<OrderTransitionResponse>(`/api/orders/${encodeURIComponent(orderId)}/cancel`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });

export const getOrder = (orderId: string): Promise<Order> =>
  requestJson<Order>(`/api/orders/${encodeURIComponent(orderId)}`, {
    method: "GET",
  });
