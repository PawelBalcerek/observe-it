import { describe, expect, it } from "vitest";
import { createEmptyScenarioCounts, selectWeightedScenario } from "./scenarios.js";

describe("selectWeightedScenario", () => {
  it("distributes 100 evenly spaced rolls according to configured scenario weights", () => {
    // given
    const counts = createEmptyScenarioCounts();

    // when
    for (let i = 0; i < 100; i += 1) {
      const scenario = selectWeightedScenario((i + 0.5) / 100);
      counts[scenario] += 1;
    }

    // then
    expect(counts).toEqual({
      HAPPY_PATH: 52,
      LOW_STOCK_WARNING: 15,
      OUT_OF_STOCK: 10,
      PAYMENT_DECLINED: 8,
      PAYMENT_GATEWAY_TIMEOUT: 5,
      BUYER_CANCELLATION: 6,
      BROWSER_DIAGNOSTICS: 4,
    });
  });
});
