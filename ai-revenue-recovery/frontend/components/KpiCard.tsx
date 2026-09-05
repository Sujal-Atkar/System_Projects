export function KpiCard({
  label,
  value,
  tone = "ink",
  size = "default",
}: {
  label: string;
  value: string;
  tone?: "ink" | "risk" | "recovered" | "ledger";
  size?: "default" | "large";
}) {
  const toneClass = {
    ink: "text-ink",
    risk: "text-risk",
    recovered: "text-recovered",
    ledger: "text-ledger",
  }[tone];

  return (
    <div className="panel p-5">
      <div className="text-xs font-medium text-slate mb-2">{label}</div>
      <div
        className={`kpi-number ${toneClass} ${
          size === "large" ? "text-4xl" : "text-2xl"
        }`}
      >
        {value}
      </div>
    </div>
  );
}
