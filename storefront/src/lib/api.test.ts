import { describe, expect, it } from "vitest";
import { stubObservedAppApi } from "../test/observedAppApiFixture.ts";
import { anOrderLineItem } from "../test/orderFixtures.ts";
import {
  ApiError,
  cancelOrder,
  createOrder,
  getOrder,
  payOrder,
  reserveOrder,
  shipOrder,
} from "./api.ts";

const expectSingleJsonPost = (
  api: ReturnType<typeof stubObservedAppApi>,
  url: string,
  body: unknown,
): void => {
  expect(api.requests()).toHaveLength(1);
  const [request] = api.requests();
  expect(request?.url).toBe(url);
  expect(request?.method).toBe("POST");
  expect(request?.headers.get("Content-Type")).toBe("application/json");
  expect(request?.body).toEqual(body);
};

const expectApiError = (error: unknown, status: number, detail: string): void => {
  expect(error).toBeInstanceOf(ApiError);
  expect(error).toMatchObject({
    name: "ApiError",
    status,
    detail,
  });
};

describe("Observed App API client", () => {
  it("creates an order with line items", async () => {
    // given
    const api = stubObservedAppApi().stubCreateOrder("ord-1");
    const lineItem = anOrderLineItem({ sku: "SKU-WIDGET-01", quantity: 2, unitPrice: 29.99 });

    // when
    const response = await createOrder("cust-100", [lineItem]);

    // then
    expect(response).toEqual({ orderId: "ord-1", state: "CREATED" });

    // and
    expectSingleJsonPost(api, "/api/orders", {
      customerId: "cust-100",
      items: [lineItem],
    });
  });

  it("parses RFC 7807 ProblemDetail on 422 and 504 failures", async () => {
    // given
    const outOfStockDetail = "Item SKU-OUT-OF-STOCK is out of stock (requested: 1, available: 0)";
    const timeoutDetail = "Payment gateway timed out";
    stubObservedAppApi()
      .stubReserveFailure(422, outOfStockDetail)
      .stubPayFailure(504, timeoutDetail);

    // when
    const reserveError = await reserveOrder("ord-2").catch((caught: unknown) => caught);
    const payError = await payOrder("ord-2", "TIMEOUT_GATEWAY").catch((caught: unknown) => caught);

    // then
    expectApiError(reserveError, 422, outOfStockDetail);

    // and
    expectApiError(payError, 504, timeoutDetail);
  });

  it("calls pay, ship, cancel, and get endpoints with expected methods and payloads", async () => {
    // given
    const api = stubObservedAppApi()
      .stubPayOrder("ord-3")
      .stubShipOrder("ord-3")
      .stubCancelOrder("ord-4")
      .stubGetOrder({
        orderId: "ord-3",
        customerId: "cust-100",
        state: "SHIPPED",
        items: [anOrderLineItem({ sku: "SKU-WIDGET-01", unitPrice: 29.99 })],
        totalAmount: 29.99,
      });

    // when
    const paid = await payOrder("ord-3", "CREDIT_CARD");
    const shipped = await shipOrder("ord-3", "STANDARD");
    const cancelled = await cancelOrder("ord-4", "buyer cancellation request");
    const order = await getOrder("ord-3");

    // then
    expect(paid).toEqual({ orderId: "ord-3", state: "PAID" });
    expect(shipped).toEqual({ orderId: "ord-3", state: "SHIPPED" });
    expect(cancelled).toEqual({ orderId: "ord-4", state: "CANCELLED" });
    expect(order.state).toBe("SHIPPED");
    expect(order.totalAmount).toBe(29.99);

    // and
    expect(api.requests().map(({ url, method }) => [url, method])).toEqual([
      ["/api/orders/ord-3/pay", "POST"],
      ["/api/orders/ord-3/ship", "POST"],
      ["/api/orders/ord-4/cancel", "POST"],
      ["/api/orders/ord-3", "GET"],
    ]);
  });
});
