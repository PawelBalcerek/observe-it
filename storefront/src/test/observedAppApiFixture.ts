import { vi } from "vitest";
import type { Order, OrderState, ProblemDetail } from "../lib/types.ts";
import { anOrder, aProblemDetail } from "./orderFixtures.ts";

interface RecordedRequest {
  readonly url: string;
  readonly method: string;
  readonly headers: Headers;
  readonly body: unknown;
}

interface ObservedAppApiFixture {
  readonly stubCreateOrder: (orderId?: string) => ObservedAppApiFixture;
  readonly stubReserveOrder: (orderId?: string) => ObservedAppApiFixture;
  readonly stubReserveFailure: (status: number, detail: string) => ObservedAppApiFixture;
  readonly stubPayOrder: (orderId?: string) => ObservedAppApiFixture;
  readonly stubPayFailure: (status: number, detail: string) => ObservedAppApiFixture;
  readonly stubShipOrder: (orderId?: string) => ObservedAppApiFixture;
  readonly stubCancelOrder: (orderId?: string) => ObservedAppApiFixture;
  readonly stubGetOrder: (order?: Partial<Order>) => ObservedAppApiFixture;
  readonly stubHappyPathCheckout: (orderId?: string) => ObservedAppApiFixture;
  readonly requests: () => readonly RecordedRequest[];
}

const jsonResponse = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const transitionResponse = (orderId: string, state: OrderState, status = 200): Response =>
  jsonResponse({ orderId, state }, status);

const problemResponse = (status: number, overrides: Partial<ProblemDetail>): Response =>
  jsonResponse(aProblemDetail({ status, ...overrides }), status);

export const stubObservedAppApi = (): ObservedAppApiFixture => {
  const fetchMock = vi.fn<typeof fetch>();
  vi.stubGlobal("fetch", fetchMock);

  const fixture: ObservedAppApiFixture = {
    stubCreateOrder: (orderId = "ord-100"): ObservedAppApiFixture => {
      fetchMock.mockResolvedValueOnce(transitionResponse(orderId, "CREATED", 201));
      return fixture;
    },

    stubReserveOrder: (orderId = "ord-100"): ObservedAppApiFixture => {
      fetchMock.mockResolvedValueOnce(transitionResponse(orderId, "RESERVED"));
      return fixture;
    },

    stubReserveFailure: (status: number, detail: string): ObservedAppApiFixture => {
      fetchMock.mockResolvedValueOnce(problemResponse(status, { detail }));
      return fixture;
    },

    stubPayOrder: (orderId = "ord-100"): ObservedAppApiFixture => {
      fetchMock.mockResolvedValueOnce(transitionResponse(orderId, "PAID"));
      return fixture;
    },

    stubPayFailure: (status: number, detail: string): ObservedAppApiFixture => {
      fetchMock.mockResolvedValueOnce(problemResponse(status, { detail }));
      return fixture;
    },

    stubShipOrder: (orderId = "ord-100"): ObservedAppApiFixture => {
      fetchMock.mockResolvedValueOnce(transitionResponse(orderId, "SHIPPED"));
      return fixture;
    },

    stubCancelOrder: (orderId = "ord-100"): ObservedAppApiFixture => {
      fetchMock.mockResolvedValueOnce(transitionResponse(orderId, "CANCELLED"));
      return fixture;
    },

    stubGetOrder: (overrides: Partial<Order> = {}): ObservedAppApiFixture => {
      fetchMock.mockResolvedValueOnce(jsonResponse(anOrder(overrides)));
      return fixture;
    },

    stubHappyPathCheckout: (orderId = "ord-100"): ObservedAppApiFixture =>
      fixture
        .stubCreateOrder(orderId)
        .stubReserveOrder(orderId)
        .stubPayOrder(orderId)
        .stubShipOrder(orderId)
        .stubGetOrder({ orderId, state: "SHIPPED" }),

    requests: (): readonly RecordedRequest[] =>
      fetchMock.mock.calls.map(([input, init]) => ({
        url: String(input),
        method: init?.method ?? "GET",
        headers: new Headers(init?.headers),
        body: init?.body !== undefined ? JSON.parse(String(init.body)) : undefined,
      })),
  };

  return fixture;
};
