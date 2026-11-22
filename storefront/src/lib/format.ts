const usdFormatter: Intl.NumberFormat = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
});

export const formatCurrency = (amount: number, currency = "USD"): string => {
  if (currency === "USD") {
    return usdFormatter.format(amount);
  }
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
  }).format(amount);
};
