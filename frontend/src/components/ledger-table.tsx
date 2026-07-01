"use client";

import { useMemo, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  CATEGORY_LABELS,
  STATUS_LABELS,
  type Transaction,
  type TransactionCategory,
  type TransactionStatus,
} from "@/lib/types";

interface LedgerTableProps {
  transactions: Transaction[];
}

const STATUS_VARIANT: Record<
  TransactionStatus,
  "default" | "secondary" | "destructive" | "outline"
> = {
  RECEIVED: "outline",
  PROCESSING: "outline",
  CATEGORIZED: "secondary",
  PENDING_REVIEW: "destructive",
  FAILED: "destructive",
};

const ALL = "ALL";

interface Filters {
  dateFrom: string;
  dateTo: string;
  amountMin: string;
  amountMax: string;
  category: TransactionCategory | typeof ALL;
  status: TransactionStatus | typeof ALL;
}

const EMPTY_FILTERS: Filters = {
  dateFrom: "",
  dateTo: "",
  amountMin: "",
  amountMax: "",
  category: ALL,
  status: ALL,
};

function formatCurrency(amount: number, currency: string) {
  try {
    return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${currency}`;
  }
}

function formatDate(iso: string) {
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(iso));
}

function applyFilters(transactions: Transaction[], filters: Filters): Transaction[] {
  const fromTime = filters.dateFrom ? new Date(`${filters.dateFrom}T00:00:00`).getTime() : null;
  const toTime = filters.dateTo ? new Date(`${filters.dateTo}T23:59:59.999`).getTime() : null;
  const min = filters.amountMin === "" ? null : Number(filters.amountMin);
  const max = filters.amountMax === "" ? null : Number(filters.amountMax);

  return transactions.filter((transaction) => {
    const txTime = new Date(transaction.transactionDate).getTime();
    if (fromTime !== null && txTime < fromTime) return false;
    if (toTime !== null && txTime > toTime) return false;
    if (min !== null && !Number.isNaN(min) && transaction.amount < min) return false;
    if (max !== null && !Number.isNaN(max) && transaction.amount > max) return false;
    if (filters.category !== ALL && transaction.category !== filters.category) return false;
    if (filters.status !== ALL && transaction.status !== filters.status) return false;
    return true;
  });
}

export function LedgerTable({ transactions }: LedgerTableProps) {
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);

  const filteredTransactions = useMemo(
    () => applyFilters(transactions, filters),
    [transactions, filters],
  );

  const hasActiveFilters = JSON.stringify(filters) !== JSON.stringify(EMPTY_FILTERS);

  function updateFilter<K extends keyof Filters>(key: K, value: Filters[K]) {
    setFilters((prev) => ({ ...prev, [key]: value }));
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Ledger feed</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
          <div className="space-y-1.5">
            <Label htmlFor="filter-date-from">Date from</Label>
            <Input
              id="filter-date-from"
              type="date"
              value={filters.dateFrom}
              onChange={(e) => updateFilter("dateFrom", e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="filter-date-to">Date to</Label>
            <Input
              id="filter-date-to"
              type="date"
              value={filters.dateTo}
              onChange={(e) => updateFilter("dateTo", e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="filter-amount-min">Amount min</Label>
            <Input
              id="filter-amount-min"
              type="number"
              inputMode="decimal"
              placeholder="0"
              value={filters.amountMin}
              onChange={(e) => updateFilter("amountMin", e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="filter-amount-max">Amount max</Label>
            <Input
              id="filter-amount-max"
              type="number"
              inputMode="decimal"
              placeholder="Any"
              value={filters.amountMax}
              onChange={(e) => updateFilter("amountMax", e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="filter-category">Category</Label>
            <Select
              value={filters.category}
              onValueChange={(value) => updateFilter("category", value as Filters["category"])}
            >
              <SelectTrigger id="filter-category">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All categories</SelectItem>
                {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
                  <SelectItem key={value} value={value}>
                    {label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="filter-status">Status</Label>
            <Select
              value={filters.status}
              onValueChange={(value) => updateFilter("status", value as Filters["status"])}
            >
              <SelectTrigger id="filter-status">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All statuses</SelectItem>
                {Object.entries(STATUS_LABELS).map(([value, label]) => (
                  <SelectItem key={value} value={value}>
                    {label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="flex items-end">
            <Button
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              disabled={!hasActiveFilters}
              onClick={() => setFilters(EMPTY_FILTERS)}
            >
              Clear filters
            </Button>
          </div>
        </div>

        <div className="flex items-center justify-between gap-2">
          <p className="text-sm text-muted-foreground">
            Showing {filteredTransactions.length} of {transactions.length} transactions
          </p>
          <p className="text-xs text-muted-foreground sm:hidden">Swipe table to see more →</p>
        </div>

        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Date</TableHead>
              <TableHead>Description</TableHead>
              <TableHead className="text-right">Amount</TableHead>
              <TableHead>Category</TableHead>
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredTransactions.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  {transactions.length === 0
                    ? "No transactions yet."
                    : "No transactions match the current filters."}
                </TableCell>
              </TableRow>
            ) : (
              filteredTransactions.map((transaction) => (
                <TableRow key={transaction.id}>
                  <TableCell className="whitespace-nowrap">
                    {formatDate(transaction.transactionDate)}
                  </TableCell>
                  <TableCell>{transaction.description}</TableCell>
                  <TableCell className="text-right whitespace-nowrap">
                    {formatCurrency(transaction.amount, transaction.currency)}
                  </TableCell>
                  <TableCell>
                    {transaction.category ? (
                      <Badge variant="outline">{CATEGORY_LABELS[transaction.category]}</Badge>
                    ) : (
                      <span className="text-muted-foreground">—</span>
                    )}
                  </TableCell>
                  <TableCell>
                    <Badge variant={STATUS_VARIANT[transaction.status]}>
                      {STATUS_LABELS[transaction.status]}
                    </Badge>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
