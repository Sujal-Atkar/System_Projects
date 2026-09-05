import { KpiCard } from "@/components/KpiCard";
import { StatusPill } from "@/components/StatusPill";
import { api } from "@/lib/api";
import { formatDateTime, formatINR, formatPercent, titleCase } from "@/lib/format";
import Link from "next/link";

export const dynamic = "force-dynamic";

export default async function OverviewPage() {
  const [summary, recentCases] = await Promise.all([
    api.analyticsSummary().catch(() => null),
    api.listCases(8).catch(() => []),
  ]);

  const hasData = summary && summary.batch_size > 0;

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end justify-between gap-4 rise-in">
        <div><p className="section-kicker mb-2">Monday, 05 September 2026</p><h1 className="font-serif text-3xl font-semibold text-ink">Good morning, Jordan.</h1><p className="mt-2 text-sm text-slate">Here is how ClearPath is protecting your revenue today.</p></div>
        <Link href="/simulator" className="rounded bg-ink px-4 py-2.5 text-sm font-medium text-white transition hover:bg-ledger">Create a scenario <span className="ml-2">→</span></Link>
      </div>

      {!hasData ? (
        <EmptyState />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <KpiCard label="Revenue at risk" value={formatINR(summary!.revenue_at_risk)} tone="risk" size="large" />
            <KpiCard
              label="Revenue recovered"
              value={formatINR(summary!.revenue_recovered)}
              tone="recovered"
              size="large"
            />
            <KpiCard label="Recovery rate" value={formatPercent(summary!.recovery_rate)} tone="ledger" />
            <KpiCard label="Active recovery cases" value={recentCases.filter((item) => item.status !== "RECOVERED").length.toString()} tone="ink" />
          </div>
        </>
      )}

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[1.5fr_1fr]">
      <div className="panel p-6 rise-in stagger-1">
        <div className="mb-5 flex items-center justify-between"><div><p className="section-kicker mb-1">Live workspace</p><h2 className="text-lg font-semibold text-ink">Recovery activity</h2></div><Link href="/cases" className="text-xs font-medium text-ledger hover:underline">View all cases →</Link></div>
        {recentCases.length === 0 ? (
          <p className="py-8 text-sm text-slate-light">No cases yet. Trigger one from the Simulator.</p>
        ) : (
          <div className="divide-y divide-slate-faint">{recentCases.slice(0, 6).map((c) => <Link href={`/cases/${c.id}`} key={c.id} className="flex items-center justify-between gap-4 py-4 first:pt-0 last:pb-0 hover:bg-paper-raised/50"><div className="flex min-w-0 items-center gap-3"><span className={`h-2.5 w-2.5 shrink-0 rounded-full ${c.status === "RECOVERED" ? "bg-recovered" : "bg-risk"}`} /><div className="min-w-0"><p className="truncate text-sm font-medium text-ink">{titleCase(c.failure_type)}</p><p className="mt-1 text-xs text-slate-light">{c.external_reference} · {formatDateTime(c.created_at)}</p></div></div><div className="shrink-0 text-right"><p className="kpi-number text-sm text-ink">{formatINR(c.amount)}</p><StatusPill status={c.status} /></div></Link>)}</div>
        )}
      </div>
      <div className="panel bg-ink p-6 text-white rise-in stagger-2"><p className="section-kicker text-white/50">The ClearPath promise</p><h2 className="mt-3 font-serif text-2xl leading-tight">Every failed payment deserves a thoughtful next step.</h2><p className="mt-4 text-sm leading-6 text-white/65">We understand the reason, check whether recovery is worth it, and only then ask the payment system to act.</p><div className="mt-8 flex items-center gap-2 text-xs text-white/70"><span className="h-2 w-2 rounded-full bg-recovered" /> Policy-protected execution</div></div>
      </div>
    </div>
  );
}

function EmptyState() {
  return (
    <div className="panel p-10 text-center">
      <p className="text-sm text-slate mb-3">No revenue-risk data yet.</p>
      <p className="text-sm text-slate-light mb-5">
        Trigger a failure scenario in the Simulator, or run{" "}
        <code className="text-xs bg-paper-raised px-1.5 py-0.5 rounded">
          python scripts/generate_batch.py
        </code>{" "}
        for a full batch.
      </p>
      <Link href="/simulator" className="inline-block text-sm text-white bg-ledger px-4 py-2 rounded hover:opacity-90">
        Open Simulator
      </Link>
    </div>
  );
}

function BreakdownList({ data }: { data: Record<string, number> }) {
  const entries = Object.entries(data).sort((a, b) => b[1] - a[1]);
  const total = entries.reduce((sum, [, v]) => sum + v, 0) || 1;
  return (
    <div className="space-y-2.5">
      {entries.map(([key, value]) => (
        <div key={key}>
          <div className="flex justify-between text-xs mb-1">
            <span className="text-slate">{titleCase(key)}</span>
            <span className="text-ink font-medium">{value}</span>
          </div>
          <div className="h-1.5 bg-paper-raised rounded-full overflow-hidden">
            <div
              className="h-full bg-ledger rounded-full"
              style={{ width: `${(value / total) * 100}%` }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}

function ThreeWayBar({ doNothing, blindRetry, ai }: { doNothing: number; blindRetry: number; ai: number }) {
  const max = Math.max(doNothing, blindRetry, ai, 1);
  const rows = [
    { label: "Do nothing", value: doNothing, color: "bg-slate-faint" },
    { label: "Blind retry", value: blindRetry, color: "bg-risk" },
    { label: "AI recovery", value: ai, color: "bg-recovered" },
  ];
  return (
    <div className="space-y-3">
      {rows.map((row) => (
        <div key={row.label} className="flex items-center gap-3">
          <div className="w-24 text-xs text-slate">{row.label}</div>
          <div className="flex-1 h-6 bg-paper-raised rounded overflow-hidden">
            <div className={`h-full ${row.color}`} style={{ width: `${(row.value / max) * 100}%` }} />
          </div>
          <div className="w-28 text-right kpi-number text-sm">{formatINR(row.value)}</div>
        </div>
      ))}
    </div>
  );
}
