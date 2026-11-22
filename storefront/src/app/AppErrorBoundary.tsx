import { Component, type ErrorInfo, type ReactNode } from "react";
import { ErrorAlert } from "../components/ErrorAlert.tsx";
import { recordRenderError } from "../telemetry/index.ts";

interface AppErrorBoundaryProps {
  readonly children: ReactNode;
}

interface AppErrorBoundaryState {
  readonly error: Error | null;
}

export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  override state: AppErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): AppErrorBoundaryState {
    return { error };
  }

  override componentDidCatch(error: Error, info: ErrorInfo): void {
    recordRenderError(error, info.componentStack ?? undefined);
  }

  private readonly handleReset = (): void => {
    this.setState({ error: null });
  };

  override render(): ReactNode {
    if (this.state.error) {
      return (
        <section aria-labelledby="error-boundary-heading" className="space-y-4 py-8">
          <h1 id="error-boundary-heading" className="text-2xl font-bold text-slate-900">
            Something went wrong
          </h1>
          <ErrorAlert message={this.state.error.message} />
          <button
            type="button"
            onClick={this.handleReset}
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
          >
            Reset
          </button>
        </section>
      );
    }

    return this.props.children;
  }
}
