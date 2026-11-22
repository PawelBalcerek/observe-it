import { type ChangeEvent, type FC, type FormEvent, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { ErrorAlert } from "../../components/ErrorAlert.tsx";
import { ApiError, cancelOrder, payOrder, shipOrder } from "../../lib/api.ts";
import type { OrderNavigationState, PaymentMethod } from "../../lib/types.ts";
import { recordOrderFailureLog, withCheckoutCompleteSpan } from "../../telemetry/index.ts";

interface PaymentOption {
  readonly value: PaymentMethod;
  readonly label: string;
  readonly description: string;
}

const PAYMENT_OPTIONS: readonly PaymentOption[] = [
  {
    value: "CREDIT_CARD",
    label: "Credit Card",
    description: "Standard payment method expected to succeed.",
  },
  {
    value: "DECLINED_CARD",
    label: "Test Card — Declined",
    description: "Simulates an issuer decline (HTTP 422).",
  },
  {
    value: "TIMEOUT_GATEWAY",
    label: "Test Gateway — Timeout",
    description: "Simulates a payment gateway timeout (HTTP 504).",
  },
];

const DEFAULT_CANCELLATION_REASON = "buyer cancellation request";

export const CheckoutPage: FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();

  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("CREDIT_CARD");
  const [cancellationReason, setCancellationReason] = useState(DEFAULT_CANCELLATION_REASON);
  const [isProcessing, setIsProcessing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  if (!orderId) {
    return <ErrorAlert title="Missing Order" message="No order ID was provided for checkout." />;
  }

  const activeOrderId = orderId;

  const handlePayAndComplete = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setErrorMessage(null);
    setIsProcessing(true);

    try {
      await withCheckoutCompleteSpan(async () => {
        try {
          await payOrder(activeOrderId, paymentMethod);
          await shipOrder(activeOrderId, "STANDARD");
          navigate(`/orders/${activeOrderId}`);
        } catch (error) {
          if (error instanceof ApiError && (error.status === 422 || error.status === 504)) {
            recordOrderFailureLog(error.status === 504 ? "ERROR" : "WARN", error.detail, {
              "order.id": activeOrderId,
              "payment.method": paymentMethod,
              "http.response.status_code": error.status,
            });
            const navState: OrderNavigationState = { failureDetail: error.detail };
            navigate(`/orders/${activeOrderId}`, { state: navState });
            return;
          }
          throw error;
        }
      });
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "Payment failed unexpectedly. Please try again.",
      );
    } finally {
      setIsProcessing(false);
    }
  };

  const handleCancelOrder = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    const trimmedReason = cancellationReason.trim();
    if (!trimmedReason) {
      setErrorMessage("Cancellation reason is required.");
      return;
    }

    setErrorMessage(null);
    setIsProcessing(true);

    try {
      await cancelOrder(activeOrderId, trimmedReason);
      navigate(`/orders/${activeOrderId}`);
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "Unable to cancel order. Please try again.",
      );
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <section aria-labelledby="checkout-heading" className="space-y-8">
      <div className="space-y-1">
        <h1 id="checkout-heading" className="text-2xl font-bold tracking-tight text-slate-900">
          Checkout
        </h1>
        <p className="font-mono text-sm text-slate-600">Order ID: {activeOrderId}</p>
      </div>

      {errorMessage ? <ErrorAlert title="Checkout error" message={errorMessage} /> : null}

      <form
        onSubmit={handlePayAndComplete}
        className="space-y-5 rounded-xl border border-slate-200 bg-white p-6 shadow-sm"
      >
        <fieldset className="space-y-3">
          <legend className="text-base font-semibold text-slate-900">Payment Method</legend>
          <div className="space-y-2">
            {PAYMENT_OPTIONS.map((option) => (
              <label
                key={option.value}
                className="flex cursor-pointer items-start gap-3 rounded-lg border border-slate-200 p-3 hover:bg-slate-50"
              >
                <input
                  type="radio"
                  name="paymentMethod"
                  value={option.value}
                  checked={paymentMethod === option.value}
                  onChange={(): void => setPaymentMethod(option.value)}
                  className="mt-1"
                />
                <span>
                  <span className="block font-medium text-slate-900">{option.label}</span>
                  <span className="block text-xs text-slate-600">{option.description}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <button
          type="submit"
          disabled={isProcessing}
          className="rounded-lg bg-slate-900 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-slate-700 disabled:opacity-50"
        >
          {isProcessing ? "Processing…" : "Pay & Complete Order"}
        </button>
      </form>

      <form
        onSubmit={handleCancelOrder}
        className="space-y-4 rounded-xl border border-slate-200 bg-white p-6 shadow-sm"
      >
        <h2 className="text-base font-semibold text-slate-900">Cancel Order</h2>
        <div className="space-y-1">
          <label htmlFor="cancellation-reason" className="block text-sm font-medium text-slate-700">
            Cancellation Reason
          </label>
          <input
            id="cancellation-reason"
            name="reason"
            type="text"
            required
            value={cancellationReason}
            onChange={(event: ChangeEvent<HTMLInputElement>): void =>
              setCancellationReason(event.target.value)
            }
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 sm:max-w-md"
          />
        </div>
        <button
          type="submit"
          disabled={isProcessing}
          className="rounded-lg border border-rose-300 bg-rose-50 px-4 py-2 text-sm font-medium text-rose-800 transition hover:bg-rose-100 disabled:opacity-50"
        >
          Cancel Order
        </button>
      </form>
    </section>
  );
};
