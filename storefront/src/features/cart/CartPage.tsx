import { type ChangeEvent, type FC, type FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router";
import { ErrorAlert } from "../../components/ErrorAlert.tsx";
import { ApiError, createOrder, reserveOrder } from "../../lib/api.ts";
import { useCart } from "../../lib/cartContext.ts";
import { formatCurrency } from "../../lib/format.ts";
import type { OrderNavigationState } from "../../lib/types.ts";
import { recordOrderFailureLog, withCheckoutStartSpan } from "../../telemetry/index.ts";

const DEFAULT_CUSTOMER_ID = "cust-storefront-01";

export const CartPage: FC = () => {
  const { items, updateQuantity, removeItem, clearCart } = useCart();
  const navigate = useNavigate();
  const [customerId, setCustomerId] = useState(DEFAULT_CUSTOMER_ID);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const totalAmount = items.reduce((sum, item) => sum + item.product.unitPrice * item.quantity, 0);

  const handleProceedToPayment = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    const trimmedCustomerId = customerId.trim();
    if (!trimmedCustomerId) {
      setErrorMessage("Customer ID is required.");
      return;
    }

    setErrorMessage(null);
    setIsSubmitting(true);

    try {
      await withCheckoutStartSpan(async () => {
        const lineItems = items.map((item) => ({
          sku: item.product.sku,
          quantity: item.quantity,
          unitPrice: item.product.unitPrice,
          currency: item.product.currency,
        }));

        const created = await createOrder(trimmedCustomerId, lineItems);

        try {
          await reserveOrder(created.orderId);
          clearCart();
          navigate(`/checkout/${created.orderId}`);
        } catch (error) {
          if (error instanceof ApiError && error.status === 422) {
            recordOrderFailureLog(
              "WARN",
              error.detail,
              {
                "order.id": created.orderId,
                "http.response.status_code": error.status,
              },
              error,
            );
            clearCart();
            const navState: OrderNavigationState = { failureDetail: error.detail };
            navigate(`/orders/${created.orderId}`, { state: navState });
            return;
          }
          throw error;
        }
      });
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "Unable to start checkout. Please try again.",
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  if (items.length === 0) {
    return (
      <section aria-labelledby="cart-heading" className="space-y-4">
        <h1 id="cart-heading" className="text-2xl font-bold tracking-tight text-slate-900">
          Your Cart
        </h1>
        <p className="text-sm text-slate-600">Your cart is currently empty.</p>
        <Link
          to="/"
          className="inline-flex rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
        >
          Browse Catalog
        </Link>
      </section>
    );
  }

  return (
    <section aria-labelledby="cart-heading" className="space-y-6">
      <h1 id="cart-heading" className="text-2xl font-bold tracking-tight text-slate-900">
        Your Cart
      </h1>

      {errorMessage ? <ErrorAlert title="Checkout error" message={errorMessage} /> : null}

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-600">
            <tr>
              <th scope="col" className="px-4 py-3">
                Item
              </th>
              <th scope="col" className="px-4 py-3">
                Unit Price
              </th>
              <th scope="col" className="px-4 py-3">
                Quantity
              </th>
              <th scope="col" className="px-4 py-3">
                Subtotal
              </th>
              <th scope="col" className="px-4 py-3">
                <span className="sr-only">Actions</span>
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200">
            {items.map((item) => (
              <tr key={item.product.sku}>
                <td className="px-4 py-3">
                  <div className="font-medium text-slate-900">{item.product.name}</div>
                  <div className="font-mono text-xs text-slate-500">{item.product.sku}</div>
                </td>
                <td className="px-4 py-3 text-slate-700">
                  {formatCurrency(item.product.unitPrice, item.product.currency)}
                </td>
                <td className="px-4 py-3">
                  <div className="inline-flex items-center gap-2">
                    <button
                      type="button"
                      aria-label={`Decrease quantity for ${item.product.name}`}
                      onClick={(): void => updateQuantity(item.product.sku, item.quantity - 1)}
                      className="rounded border border-slate-300 px-2 py-0.5 text-slate-700 hover:bg-slate-100"
                    >
                      −
                    </button>
                    <span className="min-w-6 text-center font-medium">{item.quantity}</span>
                    <button
                      type="button"
                      aria-label={`Increase quantity for ${item.product.name}`}
                      onClick={(): void => updateQuantity(item.product.sku, item.quantity + 1)}
                      className="rounded border border-slate-300 px-2 py-0.5 text-slate-700 hover:bg-slate-100"
                    >
                      +
                    </button>
                  </div>
                </td>
                <td className="px-4 py-3 font-medium text-slate-900">
                  {formatCurrency(item.product.unitPrice * item.quantity, item.product.currency)}
                </td>
                <td className="px-4 py-3 text-right">
                  <button
                    type="button"
                    aria-label={`Remove ${item.product.name}`}
                    onClick={(): void => removeItem(item.product.sku)}
                    className="text-xs font-medium text-rose-700 hover:underline"
                  >
                    Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <form
        onSubmit={handleProceedToPayment}
        className="flex flex-col gap-4 rounded-xl border border-slate-200 bg-white p-5 shadow-sm sm:flex-row sm:items-end sm:justify-between"
      >
        <div className="space-y-1">
          <label htmlFor="customer-id" className="block text-sm font-medium text-slate-700">
            Customer ID
          </label>
          <input
            id="customer-id"
            name="customerId"
            type="text"
            required
            value={customerId}
            onChange={(event: ChangeEvent<HTMLInputElement>): void =>
              setCustomerId(event.target.value)
            }
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 sm:w-64"
          />
        </div>

        <div className="flex flex-col items-start gap-3 sm:items-end">
          <p className="text-base font-bold text-slate-900">
            Total: {formatCurrency(totalAmount, "USD")}
          </p>
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded-lg bg-slate-900 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-slate-700 disabled:opacity-50"
          >
            {isSubmitting ? "Reserving Order…" : "Proceed to Payment"}
          </button>
        </div>
      </form>
    </section>
  );
};
