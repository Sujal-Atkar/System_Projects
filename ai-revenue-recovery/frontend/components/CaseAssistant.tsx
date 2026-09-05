"use client";

import { api } from "@/lib/api";
import { formatINR, formatPercent, titleCase } from "@/lib/format";
import { useState } from "react";
import type { CaseDetailOut } from "@/types/recovery";

const QUESTIONS = [
  "Why did you choose Provider B?",
  "Why not retry Provider A?",
  "Is recovery economically worthwhile?",
  "What happens if this recovery fails?",
  "Why was this case blocked?",
];

type Message = { role: "user" | "assistant"; text: string; source?: string };

export function CaseAssistant({ data, analysis }: { data: CaseDetailOut; analysis: CaseDetailOut["analyses"][number] | undefined }) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [selectedQuestion, setSelectedQuestion] = useState(QUESTIONS[0]);
  const [loading, setLoading] = useState(false);
  const [unavailable, setUnavailable] = useState(false);

  async function ask(message: string) {
    setSelectedQuestion(message);
    setLoading(true);
    setUnavailable(false);
    setMessages((current) => [...current, { role: "user", text: message }]);
    try {
      const response = await api.askAssistant(data.id, message);
      setMessages((current) => [...current, { role: "assistant", text: response.answer, source: response.source }]);
    } catch {
      setUnavailable(true);
      setMessages((current) => [...current, { role: "assistant", text: "AI explanation is temporarily unavailable. The current recovery state is shown from verified system data.", source: "FALLBACK" }]);
    } finally {
      setLoading(false);
    }
  }

  return <aside className="panel h-fit overflow-hidden">
    <div className="border-b border-slate-faint p-5"><div className="flex items-center gap-2"><span className="flex h-7 w-7 items-center justify-center rounded-full bg-ledger-bg text-ledger">✦</span><div><p className="text-sm font-semibold text-ink">Recovery assistant</p><p className="text-[10px] text-slate-light">Verified case context only</p></div></div></div>
    <div className="space-y-3 p-5">
      {messages.length > 0 && <div className="max-h-64 space-y-3 overflow-y-auto">{messages.map((message, index) => <div key={`${message.role}-${index}`} className={message.role === "user" ? "rounded bg-paper-raised p-3 text-xs text-ink" : "rounded border border-slate-faint p-3 text-xs leading-5 text-slate"}>{message.role === "assistant" && <div className="mb-1 text-[10px] font-semibold uppercase tracking-wide text-ledger">{message.source === "AI" ? "AI explanation" : "Verified fallback"}</div>}{message.text}</div>)}</div>}
      {messages.length === 0 && <p className="text-xs leading-5 text-slate">Ask why the system recommended an action, how the economics work, or what happens next. This assistant cannot execute payments.</p>}
      <div className="space-y-2">{QUESTIONS.map((question) => <button key={question} onClick={() => ask(question)} disabled={loading} className={`w-full rounded px-3 py-2 text-left text-xs transition ${selectedQuestion === question ? "bg-ledger-bg text-ledger" : "bg-paper-raised text-slate hover:text-ink"}`}>{question}</button>)}</div>
      {loading && <div className="flex items-center gap-2 text-xs text-slate-light"><span className="h-2 w-2 animate-pulse rounded-full bg-ledger" /> Asking the recovery model...</div>}
      {unavailable && <p className="text-[10px] leading-4 text-slate-light">AI is unavailable. Verified case data and payment execution are unaffected.</p>}
      <div className="border-t border-slate-faint pt-3 text-[10px] leading-4 text-slate-light">Case {data.external_reference}. Facts come from the backend; estimates are labeled. {analysis ? `Latest confidence estimate: ${formatPercent(analysis.confidence)}.` : "Analysis is not available yet."}</div>
    </div>
  </aside>;
}
