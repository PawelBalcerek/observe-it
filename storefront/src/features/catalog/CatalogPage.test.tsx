import { render, screen, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CartProvider } from "../../lib/CartProvider.tsx";
import type { DraftOrderLineItem } from "../../lib/types.ts";
import { CatalogPage } from "./CatalogPage.tsx";

const renderCatalogPage = (initialItems: readonly DraftOrderLineItem[] = []): UserEvent => {
  const user = userEvent.setup();
  render(
    <CartProvider initialItems={initialItems}>
      <CatalogPage />
    </CartProvider>,
  );
  return user;
};

const userClicksAddToCart = async (user: UserEvent, productName: RegExp): Promise<void> => {
  await user.click(
    screen.getByRole("button", { name: new RegExp(`add ${productName.source} to cart`, "i") }),
  );
};

const expectCatalogBadgesVisible = (): void => {
  expect(screen.getByRole("heading", { name: /product catalog/i })).toBeInTheDocument();
  expect(screen.getByText("Low stock — 2 left")).toBeInTheDocument();
  expect(screen.getByText("Limited availability")).toBeInTheDocument();
  expect(screen.getByText("SKU-LOW-STOCK")).toBeInTheDocument();
  expect(screen.getByText("SKU-OUT-OF-STOCK")).toBeInTheDocument();
};

const expectInCartCount = (productName: RegExp, count: number): void => {
  const card = screen.getByRole("article", { name: productName });
  expect(within(card).getByText(`${count} in cart`)).toBeInTheDocument();
};

const expectStatusAnnouncement = (pattern: RegExp): void => {
  expect(screen.getByRole("status")).toHaveTextContent(pattern);
};

describe("CatalogPage", () => {
  it("renders standard, low-stock, and out-of-stock USD products with accessible badges", () => {
    // given
    const emptyItems: readonly DraftOrderLineItem[] = [];

    // when
    renderCatalogPage(emptyItems);

    // then
    expectCatalogBadgesVisible();
  });

  it("allows adding both standard and out-of-stock items to the cart", async () => {
    // given
    const user = renderCatalogPage();

    // when
    await userClicksAddToCart(user, /telemetry ceramic mug/i);
    await userClicksAddToCart(user, /limited edition collector pin/i);

    // then
    expectInCartCount(/telemetry ceramic mug/i, 1);
    expectInCartCount(/limited edition collector pin/i, 1);

    // and
    expectStatusAnnouncement(/limited edition collector pin/i);
  });
});
