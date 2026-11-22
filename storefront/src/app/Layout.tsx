import type { FC, ReactNode } from "react";
import { Link } from "react-router";
import { useCart } from "../lib/cartContext.ts";

interface LayoutProps {
  readonly children: ReactNode;
}

export const Layout: FC<LayoutProps> = ({ children }: LayoutProps) => {
  const { lineItemCount } = useCart();

  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-4 sm:px-6">
          <Link to="/" className="text-lg font-bold tracking-tight text-slate-900">
            Observe-It Storefront
          </Link>
          <nav aria-label="Primary" className="flex items-center gap-6 text-sm font-medium">
            <Link to="/" className="text-slate-700 hover:text-slate-900">
              Catalog
            </Link>
            <Link to="/cart" className="text-slate-700 hover:text-slate-900">
              Cart ({lineItemCount})
            </Link>
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8 sm:px-6">{children}</main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-4 text-xs text-slate-500 sm:px-6">
          <span>Observe-It Reference Storefront</span>
          <Link to="/diagnostics" className="hover:text-slate-800 hover:underline">
            Diagnostics
          </Link>
        </div>
      </footer>
    </div>
  );
};
