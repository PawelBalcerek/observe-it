import type { FC } from "react";
import type { OrderState } from "../lib/types.ts";

const STATE_STYLES: Record<OrderState, string> = {
  CREATED: "bg-slate-100 text-slate-800 border-slate-300",
  RESERVED: "bg-sky-100 text-sky-900 border-sky-300",
  PAID: "bg-indigo-100 text-indigo-900 border-indigo-300",
  SHIPPED: "bg-emerald-100 text-emerald-900 border-emerald-300",
  FAILED: "bg-rose-100 text-rose-900 border-rose-300",
  CANCELLED: "bg-amber-100 text-amber-900 border-amber-300",
};

interface OrderStateBadgeProps {
  readonly state: OrderState;
}

export const OrderStateBadge: FC<OrderStateBadgeProps> = ({ state }: OrderStateBadgeProps) => (
  <span
    className={`inline-flex items-center rounded-full border px-3 py-0.5 text-sm font-semibold ${STATE_STYLES[state]}`}
  >
    {state}
  </span>
);
