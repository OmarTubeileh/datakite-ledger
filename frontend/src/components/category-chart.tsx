"use client";

import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { CATEGORY_LABELS, type CategorySummary } from "@/lib/types";

interface CategoryChartProps {
  data: CategorySummary[];
}

const chartConfig: ChartConfig = {
  totalAmountUsd: {
    label: "Total amount",
    color: "hsl(var(--chart-1))",
  },
};

export function CategoryChart({ data }: CategoryChartProps) {
  const chartData = data.map((row) => ({
    category: CATEGORY_LABELS[row.category],
    totalAmountUsd: row.totalAmountUsd,
  }));

  return (
    <Card>
      <CardHeader>
        <CardTitle>Category analytics</CardTitle>
      </CardHeader>
      <CardContent>
        {chartData.length === 0 ? (
          <p className="text-sm text-muted-foreground">No data yet.</p>
        ) : (
          <ChartContainer config={chartConfig} className="h-[280px] w-full sm:h-[320px]">
            <BarChart data={chartData} margin={{ bottom: 16 }}>
              <CartesianGrid vertical={false} />
              <XAxis
                dataKey="category"
                tickLine={false}
                axisLine={false}
                interval={0}
                angle={-25}
                textAnchor="end"
                height={50}
                tick={{ fontSize: 12 }}
              />
              <YAxis tickLine={false} axisLine={false} width={48} />
              <ChartTooltip content={<ChartTooltipContent />} />
              <Bar dataKey="totalAmountUsd" fill="var(--color-totalAmountUsd)" radius={4} />
            </BarChart>
          </ChartContainer>
        )}
      </CardContent>
    </Card>
  );
}
