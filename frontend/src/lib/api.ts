import type { CategorySummary, Transaction } from "@/lib/types";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export async function fetchTransactions(): Promise<Transaction[]> {
  const res = await fetch(`${API_URL}/api/v1/transactions`, { cache: "no-store" });
  if (!res.ok) {
    throw new Error(`Failed to fetch transactions (${res.status})`);
  }
  return res.json();
}

export async function fetchCategorySummary(): Promise<CategorySummary[]> {
  const res = await fetch(`${API_URL}/api/v1/transactions/analytics/by-category`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to fetch category summary (${res.status})`);
  }
  return res.json();
}
