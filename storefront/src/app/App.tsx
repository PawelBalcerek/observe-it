import type { FC } from "react";
import { BrowserRouter } from "react-router";
import { AppRoutes } from "./AppRoutes.tsx";

export const App: FC = () => (
  <BrowserRouter>
    <AppRoutes />
  </BrowserRouter>
);
