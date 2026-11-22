import { type FC, type ReactNode, useCallback, useMemo, useState } from "react";
import { CartContext, type CartContextValue } from "./cartContext.ts";
import type { DraftOrderLineItem, Product } from "./types.ts";

interface CartProviderProps {
  readonly initialItems?: readonly DraftOrderLineItem[];
  readonly children: ReactNode;
}

export const CartProvider: FC<CartProviderProps> = ({
  initialItems = [],
  children,
}: CartProviderProps) => {
  const [items, setItems] = useState<readonly DraftOrderLineItem[]>(initialItems);

  const lineItemCount = useMemo(
    () => items.reduce((total, item) => total + item.quantity, 0),
    [items],
  );

  const quantities = useMemo<Readonly<Record<string, number>>>(() => {
    const map: Record<string, number> = {};
    for (const item of items) {
      map[item.product.sku] = item.quantity;
    }
    return map;
  }, [items]);

  const addItem = useCallback((product: Product): void => {
    setItems((current) => {
      const existing = current.find((item) => item.product.sku === product.sku);
      if (existing) {
        return current.map((item) =>
          item.product.sku === product.sku ? { ...item, quantity: item.quantity + 1 } : item,
        );
      }
      return [...current, { product, quantity: 1 }];
    });
  }, []);

  const removeItem = useCallback((sku: string): void => {
    setItems((current) => current.filter((item) => item.product.sku !== sku));
  }, []);

  const updateQuantity = useCallback(
    (sku: string, quantity: number): void => {
      if (quantity <= 0) {
        removeItem(sku);
        return;
      }
      setItems((current) =>
        current.map((item) => (item.product.sku === sku ? { ...item, quantity } : item)),
      );
    },
    [removeItem],
  );

  const clearCart = useCallback((): void => {
    setItems([]);
  }, []);

  const value = useMemo<CartContextValue>(
    () => ({
      items,
      lineItemCount,
      quantities,
      addItem,
      updateQuantity,
      removeItem,
      clearCart,
    }),
    [items, lineItemCount, quantities, addItem, updateQuantity, removeItem, clearCart],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
};
