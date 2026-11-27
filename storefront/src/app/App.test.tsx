import { render, screen, waitFor } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";
import type { OrderState } from "../lib/types.ts";
import { silenceConsoleError } from "../test/consoleFixture.ts";
import { stubObservedAppApi } from "../test/observedAppApiFixture.ts";
import { AppRoutes } from "./AppRoutes.tsx";

const renderApp = (initialPath = "/"): UserEvent => {
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AppRoutes />
    </MemoryRouter>,
  );
  return user;
};

const userClicksAddToCart = async (user: UserEvent, productName: RegExp): Promise<void> => {
  await user.click(
    screen.getByRole("button", { name: new RegExp(`add ${productName.source} to cart`, "i") }),
  );
};

const userClicksCartLink = async (user: UserEvent, expectedCount: number): Promise<void> => {
  await user.click(
    screen.getByRole("link", { name: new RegExp(`cart \\(${expectedCount}\\)`, "i") }),
  );
};

const userClicksProceedToPayment = async (user: UserEvent): Promise<void> => {
  await user.click(screen.getByRole("button", { name: /proceed to payment/i }));
};

const userClicksPayAndCompleteOrder = async (user: UserEvent): Promise<void> => {
  await user.click(screen.getByRole("button", { name: /pay & complete order/i }));
};

const userClicksDiagnosticsLink = async (user: UserEvent): Promise<void> => {
  await user.click(screen.getByRole("link", { name: /diagnostics/i }));
};

const userClicksTriggerRenderError = async (user: UserEvent): Promise<void> => {
  await user.click(screen.getByRole("button", { name: /trigger render error/i }));
};

const userClicksReset = async (user: UserEvent): Promise<void> => {
  await user.click(screen.getByRole("button", { name: /reset/i }));
};

const expectHeading = async (name: RegExp): Promise<void> => {
  await waitFor(() => {
    expect(screen.getByRole("heading", { name })).toBeInTheDocument();
  });
};

const expectOrderState = async (state: OrderState): Promise<void> => {
  await waitFor(() => {
    expect(screen.getByText(state)).toBeInTheDocument();
  });
};

const expectAlertMessage = (pattern: RegExp): void => {
  expect(screen.getByRole("alert")).toHaveTextContent(pattern);
};

const expectContinueShoppingLink = (): void => {
  expect(screen.getByRole("link", { name: /continue shopping/i })).toHaveAttribute("href", "/");
};

describe("Storefront SPA end-to-end journeys", () => {
  it("completes the Catalog -> Cart -> Checkout -> Shipped Order Confirmation flow", async () => {
    // given
    stubObservedAppApi().stubHappyPathCheckout("ord-happy");
    const user = renderApp();

    // when
    await userClicksAddToCart(user, /telemetry ceramic mug/i);
    await userClicksCartLink(user, 1);
    await userClicksProceedToPayment(user);
    await expectHeading(/checkout/i);
    await userClicksPayAndCompleteOrder(user);

    // then
    await expectOrderState("SHIPPED");

    // and
    expectContinueShoppingLink();
  });

  it("navigates to /diagnostics via footer link, catches render error in Error Boundary, and recovers on Reset", async () => {
    // given
    silenceConsoleError();
    const user = renderApp();

    // when
    await userClicksDiagnosticsLink(user);
    await expectHeading(/browser diagnostics/i);
    await userClicksTriggerRenderError(user);

    // then
    await expectHeading(/something went wrong/i);
    expectAlertMessage(/simulated diagnostics render error/i);

    // when
    await userClicksReset(user);

    // then
    await expectHeading(/browser diagnostics/i);
  });
});
