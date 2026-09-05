"use client";

import { api } from "@/lib/api";
import { formatINR, titleCase } from "@/lib/format";
import Link from "next/link";
import { useState } from "react";
import type { CaseDetailOut, FailureType } from "@/types/recovery";

type Scenario = { label: string; short: string; description: string; failureType: FailureType; amount: number; action: string };
const SCENARIOS: Scenario[] = [
  { label: "Gateway timeout", short: "Provider did not respond", description: "The system may retry or reroute depending on provider health and expected recovery value.", failureType: "TRANSIENT_PROVIDER_FAILURE", amount: 7500, action: "Retry or reroute" },
  { label: "Provider degraded", short: "Elevated gateway failures", description: "A healthy alternative can protect the payment without repeating a failing route.", failureType: "PROVIDER_DEGRADED", amount: 12500, action: "Reroute to a healthy provider" },
  { label: "Insufficient funds", short: "Customer balance declined", description: "A later retry may help, but repeated attempts today are unlikely to create value.", failureType: "INSUFFICIENT_FUNDS", amount: 5200, action: "Retry later" },
  { label: "Expired payment method", short: "Customer action required", description: "Automatic retry is unlikely to help. The customer needs to update their payment method.", failureType: "EXPIRED_PAYMENT_METHOD", amount: 8900, action: "Request payment update" },
  { label: "Missed recurring collection", short: "Subscription payment missed", description: "A bounded retry and a helpful notification can recover recurring revenue gracefully.", failureType: "MISSED_COLLECTION", amount: 999, action: "Retry later and notify" },
  { label: "Small amount / floor", short: "Below economic floor", description: "Below the economic floor. Recovery may be rejected as economically inefficient.", failureType: "TRANSIENT_PROVIDER_FAILURE", amount: 45, action: "No action" },
];

export default function SimulatorPage() {
  const [scenario, setScenario] = useState(SCENARIOS[0]);
  const [amount, setAmount] = useState(String(SCENARIOS[0].amount));
  const [provider, setProvider] = useState("MOCK_PROVIDER_A");
  const [recurring, setRecurring] = useState(false);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<CaseDetailOut | null>(null);
  const [error, setError] = useState<string | null>(null);

  function choose(next: Scenario) { setScenario(next); setAmount(String(next.amount)); setResult(null); setError(null); }
  async function simulate() {
    setLoading(true); setError(null); setResult(null);
    try {
      const next = scenario.failureType === "MISSED_COLLECTION" || recurring
        ? await api.simulateMissedCollection(Number(amount) || scenario.amount)
        : await api.simulatePaymentFailure({ failure_type: scenario.failureType, amount: Number(amount) || scenario.amount, provider, auto_run: true });
      setResult(next);
    } catch (e) { setError(e instanceof Error ? e.message : "The simulation could not be started."); }
    finally { setLoading(false); }
  }
  const analysis = result?.analyses.at(-1);
  const recommendation = analysis ? analysis.diagnosis : scenario.action;

  return <div className="space-y-8">
    <div className="rise-in"><p className="section-kicker mb-2">Interactive workspace</p><h1 className="font-serif text-3xl font-semibold text-ink">Create a revenue incident</h1><p className="mt-2 max-w-2xl text-sm leading-6 text-slate">Create a realistic payment problem, then watch the recovery intelligence make a bounded decision.</p></div>
    <div className="grid grid-cols-1 gap-6 xl:grid-cols-[1.1fr_0.9fr]">
      <section className="panel p-6 rise-in stagger-1">
        <div className="mb-6 flex items-center justify-between"><div><p className="section-kicker">Step 1</p><h2 className="mt-1 text-lg font-semibold">Configure the incident</h2></div><span className="pill bg-ledger-bg text-ledger">Simulation only</span></div>
        <label className="mb-2 block text-xs font-semibold text-slate">Failure scenario</label>
        <div className="mb-6 grid grid-cols-2 gap-2 sm:grid-cols-3">{SCENARIOS.map((item) => <button key={item.label} onClick={() => choose(item)} className={`min-h-20 rounded border p-3 text-left transition ${scenario.label === item.label ? "border-ledger bg-ledger-bg ring-1 ring-ledger" : "border-slate-faint hover:border-ledger"}`}><span className="block text-xs font-semibold text-ink">{item.label}</span><span className="mt-1 block text-[11px] leading-4 text-slate-light">{item.short}</span></button>)}</div>
        <div className="grid gap-5 sm:grid-cols-2">
          <label className="text-xs font-semibold text-slate">Amount<input value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="decimal" className="mt-2 w-full rounded border border-slate-faint px-3 py-2.5 text-sm text-ink outline-none focus:border-ledger" /></label>
          <div><span className="text-xs font-semibold text-slate">Payment type</span><div className="mt-2 flex rounded border border-slate-faint p-1"><button onClick={() => setRecurring(false)} className={`flex-1 rounded px-3 py-2 text-xs ${!recurring ? "bg-ink text-white" : "text-slate"}`}>One-time</button><button onClick={() => setRecurring(true)} className={`flex-1 rounded px-3 py-2 text-xs ${recurring ? "bg-ink text-white" : "text-slate"}`}>Recurring</button></div></div>
          <div><span className="text-xs font-semibold text-slate">Provider</span><div className="mt-2 flex rounded border border-slate-faint p-1"><button onClick={() => setProvider("MOCK_PROVIDER_A")} className={`flex-1 rounded px-3 py-2 text-xs ${provider === "MOCK_PROVIDER_A" ? "bg-ledger-bg text-ledger" : "text-slate"}`}>Provider A</button><button onClick={() => setProvider("MOCK_PROVIDER_B")} className={`flex-1 rounded px-3 py-2 text-xs ${provider === "MOCK_PROVIDER_B" ? "bg-ledger-bg text-ledger" : "text-slate"}`}>Provider B</button></div></div>
          <label className="text-xs font-semibold text-slate">Provider health<select className="mt-2 w-full rounded border border-slate-faint bg-white px-3 py-2.5 text-sm" defaultValue="Healthy"><option>Healthy</option><option>Degraded</option><option>Down</option></select></label>
        </div>
        <div className="mt-6 rounded bg-paper-raised p-4"><p className="text-sm font-medium text-ink">{scenario.label}</p><p className="mt-1 text-xs leading-5 text-slate">{scenario.description}</p></div>
        {error && <p className="mt-4 rounded bg-danger-bg p-3 text-xs text-danger">{error}</p>}
        <button onClick={simulate} disabled={loading} className="mt-6 w-full rounded bg-ink px-4 py-3 text-sm font-semibold text-white transition hover:bg-ledger disabled:opacity-50">{loading ? "Running recovery intelligence..." : "Simulate payment failure  →"}</button>
      </section>
      <aside className="space-y-6">
        <div className="panel p-6 rise-in stagger-2"><p className="section-kicker">Live payment preview</p><div className="mt-5 flex items-start justify-between"><div><p className="metric-value text-ink">{formatINR(Number(amount) || 0)}</p><p className="mt-2 text-sm text-slate">{recurring ? "Recurring collection" : "One-time payment"}</p><p className="mt-1 text-xs text-slate-light">{provider.replace("MOCK_", "").replace("_", " ")}</p></div><span className="pill bg-recovered-bg text-recovered">Ready to simulate</span></div></div>
        <div className="rounded-lg border border-ledger/20 bg-ledger-bg p-6"><p className="section-kicker text-ledger/70">What happens next</p><div className="mt-4 space-y-3 text-sm text-ink"><FlowLine label="Failure detected" /><FlowLine label="Revenue at risk measured" /><FlowLine label="Recovery strategy evaluated" /><FlowLine label="Policy validates before execution" /></div></div>
      </aside>
    </div>
    {result && <SimulationResult result={result} recommendation={recommendation} />}
  </div>;
}

function FlowLine({ label }: { label: string }) { return <div className="flex items-center gap-3"><span className="h-2 w-2 rounded-full bg-ledger" /><span>{label}</span></div>; }
function SimulationResult({ result, recommendation }: { result: CaseDetailOut; recommendation: string }) {
  const analysis = result.analyses.at(-1); const plan = result.plans.at(-1);
  return <section className="panel overflow-hidden rise-in"><div className="border-b border-slate-faint bg-ink px-6 py-5 text-white"><p className="section-kicker text-white/50">Step 2 · Live recovery journey</p><div className="mt-2 flex flex-wrap items-end justify-between gap-3"><div><h2 className="text-xl font-semibold">The system saw the problem.</h2><p className="mt-1 text-sm text-white/60">Case {result.external_reference} · {titleCase(result.failure_type)}</p></div><span className="metric-value text-white">{formatINR(result.amount)}</span></div></div><div className="grid gap-5 p-6 md:grid-cols-2 xl:grid-cols-4"><JourneyItem title="Failure detected" text={result.failure_reason || titleCase(result.failure_type)} tone="risk" /><JourneyItem title="Revenue at risk" text={formatINR(result.amount)} tone="risk" /><JourneyItem title="AI recommendation" text={analysis ? titleCase(analysis.diagnosis) : recommendation} tone="ledger" /><JourneyItem title="Safety validation" text={plan ? `${plan.steps.length} bounded step${plan.steps.length === 1 ? "" : "s"} prepared` : "Policy checks complete"} tone="recovered" /></div><div className="mx-6 mb-6 flex flex-wrap items-center justify-between gap-4 rounded border border-slate-faint bg-paper-raised p-5"><div><p className="section-kicker">Decision economics</p><p className="mt-2 text-sm text-slate">Expected recovery <strong className="text-ink">{analysis ? formatINR(analysis.expected_incremental_value) : "Pending"}</strong><span className="mx-2 text-slate-faint">·</span> Intervention <strong className="text-ink">{analysis ? formatINR(analysis.intervention_cost) : "Pending"}</strong></p></div>{analysis && <div className={`rounded px-3 py-2 text-xs font-semibold ${analysis.expected_net_value > 0 ? "bg-recovered-bg text-recovered" : "bg-danger-bg text-danger"}`}>{analysis.expected_net_value > 0 ? "✓ Worth recovering" : "✕ Not economically viable"}</div>}</div><div className="flex justify-end px-6 pb-6"><Link href={`/cases/${result.id}`} className="rounded bg-ink px-4 py-2.5 text-sm font-medium text-white hover:bg-ledger">View recovery case →</Link></div></section>;
}
function JourneyItem({ title, text, tone }: { title: string; text: string; tone: "risk" | "ledger" | "recovered" }) { return <div className="relative rounded border border-slate-faint bg-white p-4"><span className={`mb-4 block h-2.5 w-2.5 rounded-full ${tone === "risk" ? "bg-risk" : tone === "recovered" ? "bg-recovered" : "bg-ledger"}`} /><p className="text-[10px] font-semibold uppercase tracking-[0.14em] text-slate-light">{title}</p><p className="mt-2 text-sm font-medium text-ink">{text}</p></div>; }
