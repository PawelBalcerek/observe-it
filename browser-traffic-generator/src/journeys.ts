import type { ScenarioName } from "./scenarios.js";

export type LocatorRole = "button" | "link" | "radio" | "heading";

export interface JourneyLocator {
  click(): Promise<void>;
  check(): Promise<void>;
  waitFor(): Promise<void>;
}

export interface JourneyPage {
  goto(url: string): Promise<unknown>;
  getByRole(role: LocatorRole, options?: { name?: string | RegExp }): JourneyLocator;
  getByText(text: string | RegExp): JourneyLocator;
  setDefaultTimeout(timeout: number): void;
}

const PRODUCT_BY_SCENARIO: Readonly<Record<Exclude<ScenarioName, "BROWSER_DIAGNOSTICS">, string>> =
  {
    HAPPY_PATH: "Telemetry Ceramic Mug",
    LOW_STOCK_WARNING: "OpenTelemetry Mechanical Keycap Set",
    OUT_OF_STOCK: "Limited Edition Collector Pin",
    PAYMENT_DECLINED: "Telemetry Ceramic Mug",
    PAYMENT_GATEWAY_TIMEOUT: "Telemetry Ceramic Mug",
    BUYER_CANCELLATION: "Distributed Trace Hoodie",
  };

const addProductAndProceedFromCart = async (
  page: JourneyPage,
  storefrontBaseUrl: string,
  productName: string,
): Promise<void> => {
  await page.goto(`${storefrontBaseUrl}/`);
  await page.getByRole("button", { name: `Add ${productName} to Cart` }).click();
  await page.getByRole("link", { name: /^Cart/ }).click();
  await page.getByRole("button", { name: "Proceed to Payment" }).click();
};

const completePayment = async (page: JourneyPage, paymentOptionPattern: RegExp): Promise<void> => {
  await page.getByRole("heading", { name: "Checkout" }).waitFor();
  await page.getByRole("radio", { name: paymentOptionPattern }).check();
  await page.getByRole("button", { name: "Pay & Complete Order" }).click();
  await page.getByRole("link", { name: "Continue Shopping" }).waitFor();
};

const runDiagnosticsJourney = async (
  page: JourneyPage,
  storefrontBaseUrl: string,
): Promise<void> => {
  await page.goto(`${storefrontBaseUrl}/diagnostics`);
  await page.getByRole("button", { name: "Simulate Slow Interaction" }).click();
  await page.getByText(/Completed slow interaction/).waitFor();
  await page.getByRole("button", { name: "Trigger Render Error" }).click();
  await page.getByRole("heading", { name: "Something went wrong" }).waitFor();
  await page.getByRole("button", { name: "Reset" }).click();
  await page.getByRole("heading", { name: "Browser Diagnostics" }).waitFor();
};

export const executeScenarioJourney = async (
  page: JourneyPage,
  storefrontBaseUrl: string,
  scenario: ScenarioName,
): Promise<void> => {
  if (scenario === "BROWSER_DIAGNOSTICS") {
    await runDiagnosticsJourney(page, storefrontBaseUrl);
    return;
  }

  const productName = PRODUCT_BY_SCENARIO[scenario];
  await addProductAndProceedFromCart(page, storefrontBaseUrl, productName);

  switch (scenario) {
    case "HAPPY_PATH":
    case "LOW_STOCK_WARNING":
      await completePayment(page, /Credit Card/);
      return;
    case "OUT_OF_STOCK":
      await page.getByRole("link", { name: "Continue Shopping" }).waitFor();
      return;
    case "PAYMENT_DECLINED":
      await completePayment(page, /Test Card — Declined/);
      return;
    case "PAYMENT_GATEWAY_TIMEOUT":
      await completePayment(page, /Test Gateway — Timeout/);
      return;
    case "BUYER_CANCELLATION":
      await page.getByRole("heading", { name: "Checkout" }).waitFor();
      await page.getByRole("button", { name: "Cancel Order" }).click();
      await page.getByRole("link", { name: "Continue Shopping" }).waitFor();
      return;
  }
};
