import { describe, expect, it } from "vitest";
import { executeScenarioJourney } from "./journeys.js";
import { createFakePage } from "./test/playwrightFixtures.js";

const BASE_URL = "http://storefront:8080";

const expectJourneySteps = (
  handle: ReturnType<typeof createFakePage>,
  expectedSteps: ReadonlyArray<string>,
): void => {
  expect(handle.actions.map((action) => `${action.type} ${action.target}`)).toEqual(expectedSteps);
};

describe("executeScenarioJourney", () => {
  it("drives HAPPY_PATH through catalog, cart, credit card payment, and order details", async () => {
    // given
    const handle = createFakePage();

    // when
    await executeScenarioJourney(handle.page, BASE_URL, "HAPPY_PATH");

    // then
    expectJourneySteps(handle, [
      "goto http://storefront:8080/",
      "click button:Add Telemetry Ceramic Mug to Cart",
      "click link:/^Cart/",
      "click button:Proceed to Payment",
      "waitFor heading:Checkout",
      "check radio:/Credit Card/",
      "click button:Pay & Complete Order",
      "waitFor link:Continue Shopping",
    ]);
  });

  it("drives LOW_STOCK_WARNING using the low-stock keycap set", async () => {
    // given
    const handle = createFakePage();

    // when
    await executeScenarioJourney(handle.page, BASE_URL, "LOW_STOCK_WARNING");

    // then
    expectJourneySteps(handle, [
      "goto http://storefront:8080/",
      "click button:Add OpenTelemetry Mechanical Keycap Set to Cart",
      "click link:/^Cart/",
      "click button:Proceed to Payment",
      "waitFor heading:Checkout",
      "check radio:/Credit Card/",
      "click button:Pay & Complete Order",
      "waitFor link:Continue Shopping",
    ]);
  });

  it("drives OUT_OF_STOCK directly from cart reservation to order details", async () => {
    // given
    const handle = createFakePage();

    // when
    await executeScenarioJourney(handle.page, BASE_URL, "OUT_OF_STOCK");

    // then
    expectJourneySteps(handle, [
      "goto http://storefront:8080/",
      "click button:Add Limited Edition Collector Pin to Cart",
      "click link:/^Cart/",
      "click button:Proceed to Payment",
      "waitFor link:Continue Shopping",
    ]);
  });

  it("drives PAYMENT_DECLINED with the declined test card option", async () => {
    // given
    const handle = createFakePage();

    // when
    await executeScenarioJourney(handle.page, BASE_URL, "PAYMENT_DECLINED");

    // then
    expectJourneySteps(handle, [
      "goto http://storefront:8080/",
      "click button:Add Telemetry Ceramic Mug to Cart",
      "click link:/^Cart/",
      "click button:Proceed to Payment",
      "waitFor heading:Checkout",
      "check radio:/Test Card — Declined/",
      "click button:Pay & Complete Order",
      "waitFor link:Continue Shopping",
    ]);
  });

  it("drives PAYMENT_GATEWAY_TIMEOUT with the timeout gateway option", async () => {
    // given
    const handle = createFakePage();

    // when
    await executeScenarioJourney(handle.page, BASE_URL, "PAYMENT_GATEWAY_TIMEOUT");

    // then
    expectJourneySteps(handle, [
      "goto http://storefront:8080/",
      "click button:Add Telemetry Ceramic Mug to Cart",
      "click link:/^Cart/",
      "click button:Proceed to Payment",
      "waitFor heading:Checkout",
      "check radio:/Test Gateway — Timeout/",
      "click button:Pay & Complete Order",
      "waitFor link:Continue Shopping",
    ]);
  });

  it("drives BUYER_CANCELLATION by cancelling at checkout", async () => {
    // given
    const handle = createFakePage();

    // when
    await executeScenarioJourney(handle.page, BASE_URL, "BUYER_CANCELLATION");

    // then
    expectJourneySteps(handle, [
      "goto http://storefront:8080/",
      "click button:Add Distributed Trace Hoodie to Cart",
      "click link:/^Cart/",
      "click button:Proceed to Payment",
      "waitFor heading:Checkout",
      "click button:Cancel Order",
      "waitFor link:Continue Shopping",
    ]);
  });

  it("drives BROWSER_DIAGNOSTICS through slow INP interaction and Error Boundary reset", async () => {
    // given
    const handle = createFakePage();

    // when
    await executeScenarioJourney(handle.page, BASE_URL, "BROWSER_DIAGNOSTICS");

    // then
    expectJourneySteps(handle, [
      "goto http://storefront:8080/diagnostics",
      "click button:Simulate Slow Interaction",
      "waitFor text:/Completed slow interaction/",
      "click button:Trigger Render Error",
      "waitFor heading:Something went wrong",
      "click button:Reset",
      "waitFor heading:Browser Diagnostics",
    ]);
  });
});
