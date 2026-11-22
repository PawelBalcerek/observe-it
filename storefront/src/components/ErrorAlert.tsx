import type { FC } from "react";

interface ErrorAlertProps {
  readonly title?: string;
  readonly message: string;
}

export const ErrorAlert: FC<ErrorAlertProps> = ({ title, message }: ErrorAlertProps) => (
  <div
    role="alert"
    className="rounded-lg border border-rose-300 bg-rose-50 px-4 py-3 text-rose-900"
  >
    {title ? <p className="font-semibold">{title}</p> : null}
    <p className="text-sm">{message}</p>
  </div>
);
