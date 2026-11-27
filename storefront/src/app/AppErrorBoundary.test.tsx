import { render, screen } from "@testing-library/react";
import type { FC } from "react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  createInMemoryTelemetryHarness,
  type InMemoryTelemetryHarness,
} from "../telemetry/testing.ts";
import { silenceConsoleError } from "../test/consoleFixture.ts";
import { AppErrorBoundary } from "./AppErrorBoundary.tsx";

const ThrowingChild: FC = () => {
  throw new Error("Simulated diagnostics render error");
};

const renderThrowingBoundary = (): void => {
  render(
    <AppErrorBoundary>
      <ThrowingChild />
    </AppErrorBoundary>,
  );
};

const expectFallbackHeading = (): void => {
  expect(screen.getByRole("heading", { name: /something went wrong/i })).toBeInTheDocument();
};

describe("AppErrorBoundary", () => {
  let harness: InMemoryTelemetryHarness;

  beforeEach(() => {
    harness = createInMemoryTelemetryHarness();
  });

  afterEach(async () => {
    await harness.shutdown();
  });

  it("emits an ERROR log with stack trace and component stack when catching a render crash", () => {
    // given
    silenceConsoleError();

    // when
    renderThrowingBoundary();

    // then
    expectFallbackHeading();

    // and
    harness.expectRenderErrorLog({
      message: "Simulated diagnostics render error",
      componentName: "ThrowingChild",
    });
  });
});
