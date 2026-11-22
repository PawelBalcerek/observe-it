export type OrderState = "CREATED" | "RESERVED" | "PAID" | "SHIPPED" | "FAILED" | "CANCELLED";

export type PaymentMethod = "CREDIT_CARD" | "DECLINED_CARD" | "TIMEOUT_GATEWAY";

export type ShippingMethod = "STANDARD";

export interface OrderLineItem {
  readonly sku: string;
  readonly quantity: number;
  readonly unitPrice: number;
  readonly currency: string;
}

export interface Order {
  readonly orderId: string;
  readonly customerId: string;
  readonly state: OrderState;
  readonly items: readonly OrderLineItem[];
  readonly totalAmount: number;
  readonly currency: string;
}

export interface OrderTransitionResponse {
  readonly orderId: string;
  readonly state: OrderState;
}

export interface ProblemDetail {
  readonly type?: string;
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  readonly instance?: string;
}

export interface Product {
  readonly sku: string;
  readonly name: string;
  readonly description: string;
  readonly unitPrice: number;
  readonly currency: "USD";
  readonly badge?: string;
}

export interface DraftOrderLineItem {
  readonly product: Product;
  readonly quantity: number;
}

export interface OrderNavigationState {
  readonly failureDetail?: string;
}
