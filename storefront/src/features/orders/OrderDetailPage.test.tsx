import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { describe, expect, it } from "vitest";
import type { OrderNavigationState, OrderState } from "../../lib/types.ts";
import { stubObservedAppApi } from "../../test/observedAppApiFixture.ts";
import { anOrderLineItem } from "../../test/orderFixtures.ts";
import { OrderDetailPage } from "./OrderDetailPage.tsx";

const renderOrderDetail = (state?: OrderNavigationState): void => {
  render(
    <MemoryRouter initialEntries={[{ pathname: "/orders/ord-77", state }]}>
      <Routes>
        <Route path="/orders/:orderId" element={<OrderDetailPage />} />
        <Route path="/" element={<div>Catalog Home</div>} />
      </Routes>
    </MemoryRouter>,
  );
};

const expectOrderState = async (state: OrderState): Promise<void> => {
  await waitFor(() => {
    expect(screen.getByText(state)).toBeInTheDocument();
  });
};

const expectAlertMessage = (message: string): void => {
  expect(screen.getByRole("alert")).toHaveTextContent(message);
};

const expectNoAlert = (): void => {
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
};

const expectContinueShoppingLink = (): void => {
  expect(screen.getByRole("link", { name: /continue shopping/i })).toHaveAttribute("href", "/");
};

describe("OrderDetailPage", () => {
  it("renders a SHIPPED order with line items and a Continue Shopping link", async () => {
    // given
    stubObservedAppApi().stubGetOrder({
      orderId: "ord-77",
      state: "SHIPPED",
      items: [anOrderLineItem({ quantity: 2 })],
      totalAmount: 36,
    });

    // when
    renderOrderDetail();

    // then
    await expectOrderState("SHIPPED");
    expect(screen.getByText("SKU-OBSERVE-MUG")).toBeInTheDocument();
    expect(screen.getAllByText("$36.00").length).toBeGreaterThanOrEqual(1);

    // and
    expectNoAlert();
    expectContinueShoppingLink();
  });

  it("renders a FAILED order with a role=alert banner carrying ProblemDetail.detail", async () => {
    // given
    stubObservedAppApi().stubGetOrder({ orderId: "ord-77", state: "FAILED" });

    // when
    renderOrderDetail({ failureDetail: "Payment declined by issuer" });

    // then
    await expectOrderState("FAILED");

    // and
    expectAlertMessage("Payment declined by issuer");
  });

  it("renders a CANCELLED order uniformly", async () => {
    // given
    stubObservedAppApi().stubGetOrder({ orderId: "ord-77", state: "CANCELLED" });

    // when
    renderOrderDetail();

    // then
    await expectOrderState("CANCELLED");

    // and
    expectNoAlert();
  });
});
