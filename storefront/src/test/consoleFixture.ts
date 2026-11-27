import { vi } from "vitest";

export const silenceConsoleError = (): void => {
  vi.spyOn(console, "error").mockImplementation(() => {});
};
