import { render, screen, waitFor } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import type { FC } from "react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { describe, expect, it } from "vitest";
import { CartProvider } from "../../lib/CartProvider.tsx";
import { useCart } from "../../lib/cartContext.ts";
import type { DraftOrderLineItem, OrderNavigationState } from "../../lib/types.ts";
import { stubObservedAppApi } from "../../test/observedAppApiFixture.ts";
import { aDraftOrderLineItem, aProduct } from "../../test/orderFixtures.ts";
import { CartPage } from "./CartPage.tsx";

const CartCountProbe: FC = () => {
  const { lineItemCount } = useCart();
  return <span role="status">Order Line Items: {lineItemCount}</span>;
};

const CheckoutProbe: FC = () => (
  <div>
    <span>Checkout Route Reached</span>
    <CartCountProbe />
  </div>
);

const OrderProbe: FC = () => {
  const location = useLocation();
  const state = location.state as OrderNavigationState | null;
  return (
    <div>
      <span>Order Route Reached</span>
      <CartCountProbe />
      {state?.failureDetail ? <span role="alert">{state.failureDetail}</span> : null}
    </div>
  );
};

const renderCartPage = (
  items: readonly DraftOrderLineItem[] = [aDraftOrderLineItem({ quantity: 2 })],
): UserEvent => {
  const user = userEvent.setup();

  render(
    <CartProvider initialItems={items}>
      <MemoryRouter initialEntries={["/cart"]}>
        <Routes>
          <Route path="/cart" element={<CartPage />} />
          <Route path="/checkout/:orderId" element={<CheckoutProbe />} />
          <Route path="/orders/:orderId" element={<OrderProbe />} />
        </Routes>
      </MemoryRouter>
    </CartProvider>,
  );

  return user;
};

const userEntersCustomerId = async (user: UserEvent, customerId: string): Promise<void> => {
  const input = screen.getByLabelText(/customer id/i);
  await user.clear(input);
  await user.type(input, customerId);
};

const userClicksProceedToPayment = async (user: UserEvent): Promise<void> => {
  await user.click(screen.getByRole("button", { name: /proceed to payment/i }));
};

const expectNavigatedToCheckout = async (): Promise<void> => {
  await waitFor(() => {
    expect(screen.getByText("Checkout Route Reached")).toBeInTheDocument();
  });
};

const expectNavigatedToOrderDetail = async (): Promise<void> => {
  await waitFor(() => {
    expect(screen.getByText("Order Route Reached")).toBeInTheDocument();
  });
};

const expectAlertMessage = async (message: string): Promise<void> => {
  await waitFor(() => {
    expect(screen.getByRole("alert")).toHaveTextContent(message);
  });
};

const expectCartCleared = (): void => {
  expect(screen.getByRole("status")).toHaveTextContent("Order Line Items: 0");
};

describe("CartPage", () => {
  it("creates and reserves an order then navigates to /checkout/:orderId", async () => {
    // given
    const api = stubObservedAppApi().stubCreateOrder("ord-100").stubReserveOrder("ord-100");
    const user = renderCartPage();
    expect(screen.getByLabelText(/customer id/i)).toHaveValue("cust-storefront-01");

    // when
    await userEntersCustomerId(user, "cust-alice");
    await userClicksProceedToPayment(user);

    // then
    await expectNavigatedToCheckout();
    expectCartCleared();

    // and
    expect(api.requests().map((request) => request.url)).toEqual([
      "/api/orders",
      "/api/orders/ord-100/reserve",
    ]);
  });

  it("navigates to /orders/:orderId with ProblemDetail.detail on 422 OUT_OF_STOCK", async () => {
    // given
    const outOfStockDetail = "Item SKU-OUT-OF-STOCK is out of stock (requested: 1, available: 0)";
    stubObservedAppApi().stubCreateOrder("ord-oos").stubReserveFailure(422, outOfStockDetail);
    const outOfStockItem = aDraftOrderLineItem({
      product: aProduct({
        sku: "SKU-OUT-OF-STOCK",
        name: "Limited Edition Collector Pin",
        unitPrice: 24,
        badge: "Limited availability",
      }),
    });
    const user = renderCartPage([outOfStockItem]);

    // when
    await userClicksProceedToPayment(user);

    // then
    await expectNavigatedToOrderDetail();
    await expectAlertMessage(outOfStockDetail);

    // and
    expectCartCleared();
  });

  it("preserves the cart and displays an error alert when reserveOrder fails with a 500", async () => {
    // given
    stubObservedAppApi().stubCreateOrder("ord-500").stubReserveFailure(500, "Backend unavailable");
    const user = renderCartPage();

    // when
    await userClicksProceedToPayment(user);

    // then
    await expectAlertMessage("Backend unavailable");

    // and
    expect(screen.getByText("Telemetry Ceramic Mug")).toBeInTheDocument();
  });
});
