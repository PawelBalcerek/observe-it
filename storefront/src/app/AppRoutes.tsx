import type { FC } from "react";
import { Route, Routes } from "react-router";
import { CartPage } from "../features/cart/CartPage.tsx";
import { CatalogPage } from "../features/catalog/CatalogPage.tsx";
import { CheckoutPage } from "../features/checkout/CheckoutPage.tsx";
import { DiagnosticsPage } from "../features/diagnostics/DiagnosticsPage.tsx";
import { OrderDetailPage } from "../features/orders/OrderDetailPage.tsx";
import { CartProvider } from "../lib/CartProvider.tsx";
import { AppErrorBoundary } from "./AppErrorBoundary.tsx";
import { Layout } from "./Layout.tsx";

export const AppRoutes: FC = () => (
  <CartProvider>
    <Layout>
      <AppErrorBoundary>
        <Routes>
          <Route path="/" element={<CatalogPage />} />
          <Route path="/cart" element={<CartPage />} />
          <Route path="/checkout/:orderId" element={<CheckoutPage />} />
          <Route path="/orders/:orderId" element={<OrderDetailPage />} />
          <Route path="/diagnostics" element={<DiagnosticsPage />} />
        </Routes>
      </AppErrorBoundary>
    </Layout>
  </CartProvider>
);
