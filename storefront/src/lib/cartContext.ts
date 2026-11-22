import { createContext, useContext } from "react";
import type { DraftOrderLineItem, Product } from "./types.ts";

export interface CartContextValue {
  readonly items: readonly DraftOrderLineItem[];
  readonly lineItemCount: number;
  readonly quantities: Readonly<Record<string, number>>;
  readonly addItem: (product: Product) => void;
  readonly updateQuantity: (sku: string, quantity: number) => void;
  readonly removeItem: (sku: string) => void;
  readonly clearCart: () => void;
}

export const CartContext: React.Context<CartContextValue | null> =
  createContext<CartContextValue | null>(null);

export const useCart = (): CartContextValue => {
  const value = useContext(CartContext);
  if (!value) {
    throw new Error("useCart must be used within a CartProvider");
  }
  return value;
};
