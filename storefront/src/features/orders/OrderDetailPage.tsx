import { type FC, useEffect, useState } from "react";
import { Link, useLocation, useParams } from "react-router";
import { ErrorAlert } from "../../components/ErrorAlert.tsx";
import { OrderStateBadge } from "../../components/OrderStateBadge.tsx";
import { getOrder } from "../../lib/api.ts";
import { formatCurrency } from "../../lib/format.ts";
import type { Order, OrderNavigationState } from "../../lib/types.ts";

export const OrderDetailPage: FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const location = useLocation();
  const navigationState = location.state as OrderNavigationState | null;

  const [order, setOrder] = useState<Order | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!orderId) {
      setLoadError("No order ID was provided.");
      setIsLoading(false);
      return;
    }

    let cancelled = false;
    setIsLoading(true);
    setLoadError(null);

    getOrder(orderId)
      .then((fetched) => {
        if (!cancelled) {
          setOrder(fetched);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setLoadError(error instanceof Error ? error.message : "Failed to load order details.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false);
        }
      });

    return (): void => {
      cancelled = true;
    };
  }, [orderId]);

  if (isLoading) {
    return (
      <section aria-labelledby="order-heading" className="space-y-4">
        <h1 id="order-heading" className="text-2xl font-bold tracking-tight text-slate-900">
          Order Details
        </h1>
        <p role="status" className="text-sm text-slate-600">
          Loading order…
        </p>
      </section>
    );
  }

  if (loadError || !order) {
    return (
      <section aria-labelledby="order-heading" className="space-y-4">
        <h1 id="order-heading" className="text-2xl font-bold tracking-tight text-slate-900">
          Order Details
        </h1>
        <ErrorAlert title="Unable to load order" message={loadError ?? "Order not found."} />
        <Link
          to="/"
          className="inline-flex rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
        >
          Continue Shopping
        </Link>
      </section>
    );
  }

  const failureDetail =
    order.state === "FAILED"
      ? (navigationState?.failureDetail ?? "This order could not be completed.")
      : navigationState?.failureDetail;

  return (
    <section aria-labelledby="order-heading" className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <h1 id="order-heading" className="text-2xl font-bold tracking-tight text-slate-900">
            Order Details
          </h1>
          <p className="font-mono text-sm text-slate-600">Order ID: {order.orderId}</p>
        </div>
        <OrderStateBadge state={order.state} />
      </div>

      {failureDetail ? <ErrorAlert title="Order failed" message={failureDetail} /> : null}

      <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <dl className="grid gap-4 text-sm sm:grid-cols-3">
          <div>
            <dt className="text-slate-500">Customer ID</dt>
            <dd className="font-medium text-slate-900">{order.customerId}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Order Line Items</dt>
            <dd className="font-medium text-slate-900">{order.items.length}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Total Amount</dt>
            <dd className="font-bold text-slate-900">
              {formatCurrency(order.totalAmount, order.currency)}
            </dd>
          </div>
        </dl>
      </div>

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-600">
            <tr>
              <th scope="col" className="px-4 py-3">
                SKU
              </th>
              <th scope="col" className="px-4 py-3">
                Quantity
              </th>
              <th scope="col" className="px-4 py-3">
                Unit Price
              </th>
              <th scope="col" className="px-4 py-3">
                Subtotal
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200">
            {order.items.map((item) => (
              <tr key={item.sku}>
                <td className="px-4 py-3 font-mono text-xs text-slate-900">{item.sku}</td>
                <td className="px-4 py-3 text-slate-700">{item.quantity}</td>
                <td className="px-4 py-3 text-slate-700">
                  {formatCurrency(item.unitPrice, item.currency)}
                </td>
                <td className="px-4 py-3 font-medium text-slate-900">
                  {formatCurrency(item.unitPrice * item.quantity, item.currency)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div>
        <Link
          to="/"
          className="inline-flex rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
        >
          Continue Shopping
        </Link>
      </div>
    </section>
  );
};
