"use client";

import { useEffect, useState } from "react";
import { CategoryChart } from "@/components/category-chart";
import { LedgerTable } from "@/components/ledger-table";
import { fetchCategorySummary, fetchTransactions } from "@/lib/api";
import type { CategorySummary, Transaction } from "@/lib/types";

const POLL_INTERVAL_MS = 5000;

export function Dashboard() {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [categories, setCategories] = useState<CategorySummary[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const [txns, summary] = await Promise.all([
          fetchTransactions(),
          fetchCategorySummary(),
        ]);
        if (!cancelled) {
          setTransactions(txns);
          setCategories(summary);
          setError(null);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load data");
        }
      }
    }

    load();
    const interval = setInterval(load, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  return (
    <div className="space-y-8">
      {error && (
        <p className="rounded-md border border-destructive/50 bg-destructive/10 px-4 py-2 text-sm text-destructive">
          {error}
        </p>
      )}
      <CategoryChart data={categories} />
      <LedgerTable transactions={transactions} />
    </div>
  );
}
