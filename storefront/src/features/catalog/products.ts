import type { Product } from "../../lib/types.ts";

export const CATALOG_PRODUCTS: readonly Product[] = [
  {
    sku: "SKU-OBSERVE-MUG",
    name: "Telemetry Ceramic Mug",
    description: "12 oz stoneware mug celebrating clean distributed traces.",
    unitPrice: 18.0,
    currency: "USD",
  },
  {
    sku: "SKU-TRACE-HOODIE",
    name: "Distributed Trace Hoodie",
    description: "Midweight cotton fleece hoodie with minimal span-waterfall artwork.",
    unitPrice: 64.0,
    currency: "USD",
  },
  {
    sku: "SKU-SPAN-NOTEBOOK",
    name: "Span Field Notebook",
    description: "Dot-grid pocket notebook for incident notes and architecture sketches.",
    unitPrice: 14.5,
    currency: "USD",
  },
  {
    sku: "SKU-METRIC-BOTTLE",
    name: "Prometheus Insulated Bottle",
    description: "Double-wall stainless steel bottle built for long on-call shifts.",
    unitPrice: 32.0,
    currency: "USD",
  },
  {
    sku: "SKU-LOW-STOCK",
    name: "OpenTelemetry Mechanical Keycap Set",
    description: "PBT keycap set accented with OpenTelemetry colors.",
    unitPrice: 45.0,
    currency: "USD",
    badge: "Low stock — 2 left",
  },
  {
    sku: "SKU-OUT-OF-STOCK",
    name: "Limited Edition Collector Pin",
    description: "Numbered enamel pin produced in a small commemorative run.",
    unitPrice: 24.0,
    currency: "USD",
    badge: "Limited availability",
  },
];
