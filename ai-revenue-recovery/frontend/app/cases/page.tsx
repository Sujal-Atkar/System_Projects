import { StatusPill } from "@/components/StatusPill";
import { api } from "@/lib/api";
import { formatDateTime, formatINR, titleCase } from "@/lib/format";
import Link from "next/link";

export const dynamic = "force-dynamic";

export default async function CasesPage() {
  const cases = await api.listCases(100).catch(() => []);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-serif text-2xl font-semibold text-ink mb-1">Recovery Cases</h1>
        <p className="text-sm text-slate">Every case the system has detected, analyzed, and acted on.</p>
      </div>

      <div className="panel overflow-hidden">
        {cases.length === 0 ? (
          <p className="p-8 text-sm text-slate-light text-center">
            No cases yet. Trigger one from the Simulator.
          </p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-slate-light border-b border-slate-faint bg-paper-raised">
                <th className="p-3 font-medium">Case</th>
                <th className="p-3 font-medium">Source</th>
                <th className="p-3 font-medium">Amount</th>
                <th className="p-3 font-medium">Failure</th>
                <th className="p-3 font-medium">Status</th>
                <th className="p-3 font-medium">Recovered</th>
                <th className="p-3 font-medium">Created</th>
              </tr>
            </thead>
            <tbody>
              {cases.map((c) => (
                <tr key={c.id} className="border-b border-slate-faint last:border-0 hover:bg-paper-raised">
                  <td className="p-3">
                    <Link href={`/cases/${c.id}`} className="text-ledger hover:underline font-medium">
                      {c.external_reference}
                    </Link>
                  </td>
                  <td className="p-3 text-slate text-xs">{titleCase(c.source_type)}</td>
                  <td className="p-3 kpi-number">{formatINR(c.amount)}</td>
                  <td className="p-3 text-slate">{titleCase(c.failure_type)}</td>
                  <td className="p-3">
                    <StatusPill status={c.status} />
                  </td>
                  <td className="p-3 kpi-number">{c.recovered_amount ? formatINR(c.recovered_amount) : "—"}</td>
                  <td className="p-3 text-slate-light text-xs">{formatDateTime(c.created_at)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
