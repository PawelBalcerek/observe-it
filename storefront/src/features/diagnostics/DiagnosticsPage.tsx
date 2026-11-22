import { type FC, useState } from "react";

const DEFAULT_SLOW_BLOCK_MS = 250;

interface DiagnosticsPageProps {
  readonly slowBlockMs?: number;
}

interface RenderCrashTriggerProps {
  readonly shouldThrow: boolean;
}

const RenderCrashTrigger: FC<RenderCrashTriggerProps> = ({
  shouldThrow,
}: RenderCrashTriggerProps) => {
  if (shouldThrow) {
    throw new Error("Simulated diagnostics render error");
  }
  return null;
};

export const DiagnosticsPage: FC<DiagnosticsPageProps> = ({
  slowBlockMs = DEFAULT_SLOW_BLOCK_MS,
}: DiagnosticsPageProps) => {
  const [statusMessage, setStatusMessage] = useState<string>("");
  const [shouldThrow, setShouldThrow] = useState(false);

  const handleSimulateSlowInteraction = (): void => {
    const start = performance.now();
    while (performance.now() - start < slowBlockMs) {
      // Intentional synchronous main-thread block to exercise INP telemetry.
    }
    const elapsedMs = Math.round(performance.now() - start);
    setStatusMessage(`Completed slow interaction (${elapsedMs} ms main-thread block).`);
  };

  return (
    <section aria-labelledby="diagnostics-heading" className="space-y-6">
      <div className="space-y-1">
        <h1 id="diagnostics-heading" className="text-2xl font-bold tracking-tight text-slate-900">
          Browser Diagnostics
        </h1>
        <p className="text-sm text-slate-600">
          Trigger client-side performance and error conditions for browser observability testing.
        </p>
      </div>

      <RenderCrashTrigger shouldThrow={shouldThrow} />

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-3 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-base font-semibold text-slate-900">Slow Interaction</h2>
          <p className="text-sm text-slate-600">
            Blocks the main thread synchronously for ~250 ms to exercise INP telemetry.
          </p>
          <button
            type="button"
            onClick={handleSimulateSlowInteraction}
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
          >
            Simulate Slow Interaction
          </button>
        </div>

        <div className="space-y-3 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-base font-semibold text-slate-900">Render Error</h2>
          <p className="text-sm text-slate-600">
            Throws inside a child component to verify React Error Boundary capture and recovery.
          </p>
          <button
            type="button"
            onClick={(): void => setShouldThrow(true)}
            className="rounded-lg border border-rose-300 bg-rose-50 px-4 py-2 text-sm font-medium text-rose-800 hover:bg-rose-100"
          >
            Trigger Render Error
          </button>
        </div>
      </div>

      <p role="status" aria-live="polite" className="min-h-5 text-sm font-medium text-slate-700">
        {statusMessage}
      </p>
    </section>
  );
};
