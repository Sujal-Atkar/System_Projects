import { api } from "@/lib/api";
import { formatINR, formatPercent, titleCase } from "@/lib/format";

export const dynamic = "force-dynamic";

export default async function AnalyticsPage() {
  const summary = await api.analyticsSummary().catch(() => null);

  if (!summary || summary.batch_size === 0) {
    return (
      <div className="space-y-6">
        <h1 className="font-serif text-2xl font-semibold text-ink">Batch Analytics</h1>
        <div className="panel p-10 text-center">
          <p className="text-sm text-slate mb-3">No batch data yet.</p>
          <p className="text-sm text-slate-light">
            Run{" "}
            <code className="text-xs bg-paper-raised px-1.5 py-0.5 rounded">
              python scripts/generate_batch.py --size 1000
            </code>{" "}
            from the backend directory to populate this page with reproducible batch results.
          </p>
        </div>
      </div>
    );
  }

  const lift =
    summary.baseline_blind_retry > 0
      ? ((summary.ai_recovered - summary.baseline_blind_retry) / summary.baseline_blind_retry) * 100
      : 0;

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-serif text-2xl font-semibold text-ink mb-1">Batch Analytics</h1>
        <p className="text-sm text-slate">
          {summary.batch_size} cases · Rs {summary.revenue_at_risk.toLocaleString("en-IN")} at risk
        </p>
      </div>

      <div className="panel p-6">
        <h2 className="text-xs font-semibold text-slate-light uppercase tracking-wide mb-5">
          Revenue recovered — same batch, same assumptions
        </h2>
        <div className="grid grid-cols-3 gap-4">
          <ComparisonCard label="Do nothing" value={summary.baseline_do_nothing} tone="ink" />
          <ComparisonCard label="Blind retry" value={summary.baseline_blind_retry} tone="risk" />
          <ComparisonCard
            label="AI recovery"
            value={summary.ai_recovered}
            tone="recovered"
            footnote={lift !== 0 ? `${lift > 0 ? "+" : ""}${lift.toFixed(1)}% vs. blind retry` : undefined}
          />
        </div>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <SmallStat label="Recovery rate" value={formatPercent(summary.recovery_rate)} />
        <SmallStat label="Intervention cost" value={formatINR(summary.intervention_cost_total)} />
        <SmallStat label="Net recovered" value={formatINR(summary.net_recovered_revenue)} />
        <SmallStat label="Batch size" value={summary.batch_size.toString()} />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="panel p-6">
          <h2 className="text-xs font-semibold text-slate-light uppercase tracking-wide mb-4">
            Recovery by failure type
          </h2>
          <BreakdownTable data={summary.failure_type_breakdown} />
        </div>
        <div className="panel p-6">
          <h2 className="text-xs font-semibold text-slate-light uppercase tracking-wide mb-4">
            Decision distribution
          </h2>
          <BreakdownTable data={summary.decision_distribution} />
        </div>
      </div>

      <div className="panel p-6 bg-paper-raised">
        <h2 className="text-xs font-semibold text-slate-light uppercase tracking-wide mb-3">
          Simulation assumptions
        </h2>
        <ul className="text-xs text-slate-light space-y-1.5 list-disc list-inside">
          <li>Blind-retry baseline applies a flat 35% assumed success rate uniformly — a naive comparison
            policy, not a measured system.</li>
          <li>&quot;Do nothing&quot; is exactly ₹0 by definition.</li>
          <li>AI recovery figures reflect actual outcomes from the seeded mock payment simulation.</li>
          <li>Cases run without a configured Gemini API key use the deterministic policy fallback rather
            than a live model call.</li>
        </ul>
      </div>
    </div>
  );
}

function ComparisonCard({
  label,
  value,
  tone,
  footnote,
}: {
  label: string;
  value: number;
  tone: "ink" | "risk" | "recovered";
  footnote?: string;
}) {
  const toneClass = { ink: "text-ink", risk: "text-risk", recovered: "text-recovered" }[tone];
  return (
    <div className="text-center py-4">
      <div className="text-xs text-slate mb-2">{label}</div>
      <div className={`kpi-number text-2xl ${toneClass}`}>{formatINR(value)}</div>
      {footnote && <div className="text-xs text-recovered mt-1">{footnote}</div>}
    </div>
  );
}

function SmallStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="panel p-4">
      <div className="text-xs text-slate mb-1">{label}</div>
      <div className="kpi-number text-lg text-ink">{value}</div>
    </div>
  );
}

function BreakdownTable({ data }: { data: Record<string, number> }) {
  const entries = Object.entries(data).sort((a, b) => b[1] - a[1]);
  return (
    <table className="w-full text-sm">
      <tbody>
        {entries.map(([key, value]) => (
          <tr key={key} className="border-b border-slate-faint last:border-0">
            <td className="py-2 text-slate">{titleCase(key)}</td>
            <td className="py-2 text-right kpi-number">{value}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
