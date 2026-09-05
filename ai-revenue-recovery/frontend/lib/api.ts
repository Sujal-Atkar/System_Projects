import type {
  AnalyticsSummaryOut,
  CaseDetailOut,
  CaseSummaryOut,
  FailureType,
} from "@/types/recovery";

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8000";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
    cache: "no-store",
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`API ${path} failed: ${res.status} ${body}`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  listCases: (limit = 50) => request<CaseSummaryOut[]>(`/api/recovery/cases?limit=${limit}`),

  getCase: (id: string) => request<CaseDetailOut>(`/api/recovery/cases/${id}`),

  executeNext: (id: string) => request<CaseDetailOut>(`/api/recovery/cases/${id}/execute-next`, { method: "POST" }),

  askAssistant: (id: string, message: string) => request<{ answer: string; source: string; case_id: string }>(`/api/recovery/cases/${id}/assistant`, {
    method: "POST",
    body: JSON.stringify({ message }),
  }),

  analyticsSummary: () => request<AnalyticsSummaryOut>(`/api/analytics/summary`),

  simulatePaymentFailure: (params: {
    failure_type: FailureType;
    amount?: number;
    provider?: string | null;
    auto_run?: boolean;
  }) =>
    request<CaseDetailOut>(`/api/simulator/payment-failure`, {
      method: "POST",
      body: JSON.stringify({
        failure_type: params.failure_type,
        amount: params.amount ?? 4999,
        provider: params.provider ?? "MOCK_PROVIDER_A",
        auto_run: params.auto_run ?? true,
      }),
    }),

  simulateMissedCollection: (amount = 999) =>
    request<CaseDetailOut>(`/api/simulator/missed-collection?amount=${amount}`, { method: "POST" }),

  setProviderHealth: (provider: string, health: "HEALTHY" | "DEGRADED" | "DOWN") =>
    request<{ provider: string; health: string }>(`/api/simulator/provider-health`, {
      method: "POST",
      body: JSON.stringify({ provider, health }),
    }),
};
