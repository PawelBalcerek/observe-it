import { type FC, useState } from "react";
import { useCart } from "../../lib/cartContext.ts";
import { formatCurrency } from "../../lib/format.ts";
import type { Product } from "../../lib/types.ts";
import { CATALOG_PRODUCTS } from "./products.ts";

export const CatalogPage: FC = () => {
  const { quantities, addItem } = useCart();
  const [statusMessage, setStatusMessage] = useState<string>("");

  const handleAdd = (product: Product): void => {
    addItem(product);
    setStatusMessage(`Added ${product.name} to cart.`);
  };

  return (
    <section aria-labelledby="catalog-heading" className="space-y-6">
      <div className="space-y-1">
        <h1 id="catalog-heading" className="text-2xl font-bold tracking-tight text-slate-900">
          Product Catalog
        </h1>
        <p className="text-sm text-slate-600">
          Select items to add Order Line Items to your cart. All prices are in USD.
        </p>
      </div>

      <p role="status" aria-live="polite" className="min-h-5 text-sm font-medium text-emerald-700">
        {statusMessage}
      </p>

      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {CATALOG_PRODUCTS.map((product) => {
          const quantityInCart = quantities[product.sku] ?? 0;
          return (
            <article
              key={product.sku}
              aria-label={product.name}
              className="flex flex-col justify-between rounded-xl border border-slate-200 bg-white p-5 shadow-sm"
            >
              <div className="space-y-2">
                <div className="flex items-start justify-between gap-2">
                  <h2 className="text-lg font-semibold text-slate-900">{product.name}</h2>
                  {product.badge ? (
                    <span className="shrink-0 rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-900">
                      {product.badge}
                    </span>
                  ) : null}
                </div>
                <p className="font-mono text-xs text-slate-500">{product.sku}</p>
                <p className="text-sm text-slate-600">{product.description}</p>
              </div>

              <div className="mt-5 flex items-center justify-between border-t border-slate-100 pt-4">
                <div>
                  <p className="text-base font-bold text-slate-900">
                    {formatCurrency(product.unitPrice, product.currency)}
                  </p>
                  {quantityInCart > 0 ? (
                    <p className="text-xs text-slate-500">{quantityInCart} in cart</p>
                  ) : null}
                </div>
                <button
                  type="button"
                  aria-label={`Add ${product.name} to Cart`}
                  onClick={(): void => handleAdd(product)}
                  className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-900"
                >
                  Add to Cart
                </button>
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
};
