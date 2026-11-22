type TelemetryLogSeverity = "WARN" | "ERROR";

export const withCheckoutStartSpan = <T>(fn: () => Promise<T>): Promise<T> => fn();

export const withCheckoutCompleteSpan = <T>(fn: () => Promise<T>): Promise<T> => fn();

export const recordOrderFailureLog = (
  _severity: TelemetryLogSeverity,
  _message: string,
  _attributes: Record<string, string | number>,
): void => {
  // Wired to OpenTelemetry browser logs in #195.
};

export const recordRenderError = (_error: Error, _componentStack?: string): void => {
  // Wired to OpenTelemetry browser logs in #195.
};
