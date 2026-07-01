export type TransactionStatus =
  | "RECEIVED"
  | "PROCESSING"
  | "CATEGORIZED"
  | "PENDING_REVIEW"
  | "FAILED";

export type TransactionCategory =
  | "SAAS_SOFTWARE"
  | "INFRASTRUCTURE"
  | "BUSINESS_MEALS"
  | "OPERATIONS"
  | "MISCELLANEOUS";

export interface Transaction {
  id: string;
  amount: number;
  currency: string;
  transactionDate: string;
  description: string;
  status: TransactionStatus;
  category: TransactionCategory | null;
}

export interface CategorySummary {
  category: TransactionCategory;
  totalAmountUsd: number;
  transactionCount: number;
}

export const CATEGORY_LABELS: Record<TransactionCategory, string> = {
  SAAS_SOFTWARE: "SaaS/Software",
  INFRASTRUCTURE: "Infrastructure",
  BUSINESS_MEALS: "Business Meals",
  OPERATIONS: "Operations",
  MISCELLANEOUS: "Miscellaneous",
};

export const STATUS_LABELS: Record<TransactionStatus, string> = {
  RECEIVED: "Received",
  PROCESSING: "Processing",
  CATEGORIZED: "Categorized",
  PENDING_REVIEW: "Pending Review",
  FAILED: "Failed",
};
