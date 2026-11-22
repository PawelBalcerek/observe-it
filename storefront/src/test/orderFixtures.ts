import type {
  DraftOrderLineItem,
  Order,
  OrderLineItem,
  ProblemDetail,
  Product,
} from "../lib/types.ts";

export const aProduct = (overrides: Partial<Product> = {}): Product => ({
  sku: "SKU-OBSERVE-MUG",
  name: "Telemetry Ceramic Mug",
  description: "Ceramic mug",
  unitPrice: 18,
  currency: "USD",
  ...overrides,
});

export const aDraftOrderLineItem = (
  overrides: Partial<DraftOrderLineItem> = {},
): DraftOrderLineItem => ({
  product: aProduct(),
  quantity: 1,
  ...overrides,
});

export const anOrderLineItem = (overrides: Partial<OrderLineItem> = {}): OrderLineItem => ({
  sku: "SKU-OBSERVE-MUG",
  quantity: 1,
  unitPrice: 18,
  currency: "USD",
  ...overrides,
});

export const anOrder = (overrides: Partial<Order> = {}): Order => {
  const items = overrides.items ?? [anOrderLineItem()];
  const totalAmount =
    overrides.totalAmount ?? items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);

  return {
    orderId: "ord-100",
    customerId: "cust-storefront-01",
    state: "SHIPPED",
    items,
    totalAmount,
    currency: "USD",
    ...overrides,
  };
};

export const aProblemDetail = (overrides: Partial<ProblemDetail> = {}): ProblemDetail => ({
  title: "Unprocessable Content",
  status: 422,
  detail: "Order processing failed",
  ...overrides,
});
