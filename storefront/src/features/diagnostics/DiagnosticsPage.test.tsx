import { render, screen } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { DiagnosticsPage } from "./DiagnosticsPage.tsx";

const renderDiagnosticsPage = (slowBlockMs = 5): UserEvent => {
  const user = userEvent.setup();
  render(<DiagnosticsPage slowBlockMs={slowBlockMs} />);
  return user;
};

const userClicksSimulateSlowInteraction = async (user: UserEvent): Promise<void> => {
  await user.click(screen.getByRole("button", { name: /simulate slow interaction/i }));
};

const expectStatusMessage = (pattern: RegExp): void => {
  expect(screen.getByRole("status")).toHaveTextContent(pattern);
};

describe("DiagnosticsPage", () => {
  it("executes a synchronous slow interaction and reports completion", async () => {
    // given
    const user = renderDiagnosticsPage();

    // when
    await userClicksSimulateSlowInteraction(user);

    // then
    expectStatusMessage(/completed slow interaction/i);
  });
});
