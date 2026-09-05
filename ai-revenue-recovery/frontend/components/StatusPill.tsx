import { titleCase } from "@/lib/format";
import type { CaseStatus, DecisionSource } from "@/types/recovery";

const STATUS_STYLES: Record<CaseStatus, string> = {
  DETECTED: "bg-slate-faint text-slate",
  ANALYZING: "bg-ledger-bg text-ledger",
  PLAN_PROPOSED: "bg-ledger-bg text-ledger",
  VALIDATED: "bg-ledger-bg text-ledger",
  EXECUTING: "bg-ledger-bg text-ledger",
  RECOVERED: "bg-recovered-bg text-recovered",
  FAILED_RECOVERY: "bg-danger-bg text-danger",
  STOPPED: "bg-slate-faint text-slate",
};

export function StatusPill({ status }: { status: CaseStatus }) {
  return <span className={`pill ${STATUS_STYLES[status]}`}>{titleCase(status)}</span>;
}

export function DecisionSourcePill({ source }: { source: DecisionSource }) {
  const style =
    source === "AI" ? "bg-ledger-bg text-ledger" : "bg-slate-faint text-slate";
  const label = source === "AI" ? "AI decision" : "Fallback decision";
  return <span className={`pill ${style}`}>{label}</span>;
}
