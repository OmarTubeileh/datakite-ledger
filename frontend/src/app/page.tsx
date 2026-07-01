import { Dashboard } from "@/components/dashboard";

export default function DashboardPage() {
  return (
    <main className="mx-auto max-w-6xl space-y-6 p-4 sm:space-y-8 sm:p-8">
      <div>
        <h1 className="text-xl font-semibold sm:text-2xl">DataKite Ledger</h1>
        <p className="text-sm text-muted-foreground">
          Async AI categorization ledger — refreshes automatically every 5 seconds.
        </p>
      </div>
      <Dashboard />
    </main>
  );
}
