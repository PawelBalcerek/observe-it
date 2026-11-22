import { render, screen, waitFor } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import type { FC } from "react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { describe, expect, it } from "vitest";
import type { OrderNavigationState } from "../../lib/types.ts";
import { stubObservedAppApi } from "../../test/observedAppApiFixture.ts";
import { CheckoutPage } from "./CheckoutPage.tsx";

const OrderRouteProbe: FC = () => {
  const location = useLocation();
  const state = location.state as OrderNavigationState | null;
  return (
    <div>
      <span>Order Detail Reached</span>
      {state?.failureDetail ? <span role="alert">{state.failureDetail}</span> : null}
    </div>
  );
};

const renderCheckout = (orderId = "ord-42"): UserEvent => {
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={[`/checkout/${orderId}`]}>
      <Routes>
        <Route path="/checkout/:orderId" element={<CheckoutPage />} />
        <Route path="/orders/:orderId" element={<OrderRouteProbe />} />
      </Routes>
    </MemoryRouter>,
  );
  return user;
};

const userSelectsPaymentMethod = async (user: UserEvent, label: RegExp): Promise<void> => {
  await user.click(screen.getByRole("radio", { name: label }));
};

const userClicksPayAndCompleteOrder = async (user: UserEvent): Promise<void> => {
  await user.click(screen.getByRole("button", { name: /pay & complete order/i }));
};

const userEntersCancellationReason = async (user: UserEvent, reason: string): Promise<void> => {
  const input = screen.getByLabelText(/cancellation reason/i);
  await user.clear(input);
  await user.type(input, reason);
};

const userClicksCancelOrder = async (user: UserEvent): Promise<void> => {
  await user.click(screen.getByRole("button", { name: /cancel order/i }));
};

const expectNavigatedToOrderDetail = async (): Promise<void> => {
  await waitFor(() => {
    expect(screen.getByText("Order Detail Reached")).toBeInTheDocument();
  });
};

const expectAlertMessage = (message: string): void => {
  expect(screen.getByRole("alert")).toHaveTextContent(message);
};

describe("CheckoutPage", () => {
  it("defaults to Credit Card, executes pay then ship (STANDARD), and navigates to /orders/:orderId", async () => {
    // given
    const api = stubObservedAppApi().stubPayOrder("ord-42").stubShipOrder("ord-42");
    const user = renderCheckout();
    expect(screen.getByRole("radio", { name: /credit card/i })).toBeChecked();

    // when
    await userClicksPayAndCompleteOrder(user);

    // then
    await expectNavigatedToOrderDetail();

    // and
    expect(api.requests().map(({ url, body }) => ({ url, body }))).toEqual([
      { url: "/api/orders/ord-42/pay", body: { paymentMethod: "CREDIT_CARD" } },
      { url: "/api/orders/ord-42/ship", body: { shippingMethod: "STANDARD" } },
    ]);
  });

  it("navigates to /orders/:orderId with ProblemDetail.detail on 422 PAYMENT_DECLINED", async () => {
    // given
    const api = stubObservedAppApi().stubPayFailure(422, "Payment declined by issuer");
    const user = renderCheckout();

    // when
    await userSelectsPaymentMethod(user, /test card — declined/i);
    await userClicksPayAndCompleteOrder(user);

    // then
    await expectNavigatedToOrderDetail();
    expectAlertMessage("Payment declined by issuer");

    // and
    expect(api.requests().map(({ url, body }) => ({ url, body }))).toEqual([
      { url: "/api/orders/ord-42/pay", body: { paymentMethod: "DECLINED_CARD" } },
    ]);
  });

  it("navigates to /orders/:orderId with ProblemDetail.detail on 504 PAYMENT_GATEWAY_TIMEOUT", async () => {
    // given
    const api = stubObservedAppApi().stubPayFailure(504, "Payment gateway timed out");
    const user = renderCheckout();

    // when
    await userSelectsPaymentMethod(user, /test gateway — timeout/i);
    await userClicksPayAndCompleteOrder(user);

    // then
    await expectNavigatedToOrderDetail();
    expectAlertMessage("Payment gateway timed out");

    // and
    expect(api.requests().map(({ url, body }) => ({ url, body }))).toEqual([
      { url: "/api/orders/ord-42/pay", body: { paymentMethod: "TIMEOUT_GATEWAY" } },
    ]);
  });

  it("cancels the order with editable reason and navigates to /orders/:orderId", async () => {
    // given
    const api = stubObservedAppApi().stubCancelOrder("ord-42");
    const user = renderCheckout();
    expect(screen.getByLabelText(/cancellation reason/i)).toHaveValue("buyer cancellation request");

    // when
    await userEntersCancellationReason(user, "changed my mind");
    await userClicksCancelOrder(user);

    // then
    await expectNavigatedToOrderDetail();

    // and
    expect(api.requests().map(({ url, body }) => ({ url, body }))).toEqual([
      { url: "/api/orders/ord-42/cancel", body: { reason: "changed my mind" } },
    ]);
  });
});
