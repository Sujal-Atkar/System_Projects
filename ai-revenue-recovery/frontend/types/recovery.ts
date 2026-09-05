export type RevenueSourceType = "ONE_OFF_PAYMENT" | "RECURRING_COLLECTION";

export type FailureType =
  | "TRANSIENT_PROVIDER_FAILURE"
  | "PROVIDER_DEGRADED"
  | "INSUFFICIENT_FUNDS"
  | "EXPIRED_PAYMENT_METHOD"
  | "INVALID_PAYMENT_METHOD"
  | "BUSINESS_DECLINE"
  | "MISSED_COLLECTION"
  | "OTHER";

export type CaseStatus =
  | "DETECTED"
  | "ANALYZING"
  | "PLAN_PROPOSED"
  | "VALIDATED"
  | "EXECUTING"
  | "RECOVERED"
  | "FAILED_RECOVERY"
  | "STOPPED";

export type RecoveryActionType =
  | "RETRY_NOW"
  | "RETRY_LATER"
  | "REROUTE"
  | "NOTIFY"
  | "REQUEST_PAYMENT_UPDATE"
  | "WAIT"
  | "ESCALATE"
  | "NO_ACTION";

export type PlanStepStatus = "PLANNED" | "PROPOSED" | "EXECUTING" | "SUCCESS" | "FAILED" | "SKIPPED";
export type DecisionSource = "AI" | "FALLBACK";

export interface AnalysisOut {
  diagnosis: string;
  recovery_probability: number;
  organic_recovery_probability: number;
  expected_incremental_value: number;
  intervention_cost: number;
  expected_net_value: number;
  reasoning: string;
  confidence: number;
  decision_source: DecisionSource;
  model_version: string | null;
  created_at: string;
}

export interface PlanStepOut {
  step_number: number;
  timing: string;
  action: RecoveryActionType;
  target_provider: string | null;
  status: PlanStepStatus;
  outcome: string | null;
  executed_at: string | null;
}

export interface PlanOut {
  id: string;
  status: CaseStatus;
  created_at: string;
  steps: PlanStepOut[];
}

export interface CaseSummaryOut {
  id: string;
  external_reference: string;
  source_type: RevenueSourceType;
  amount: number;
  currency: string;
  failure_type: FailureType;
  status: CaseStatus;
  recovered_amount: number | null;
  created_at: string;
}

export interface CaseDetailOut extends CaseSummaryOut {
  failure_reason: string | null;
  provider: string | null;
  attempt_number: number;
  analyses: AnalysisOut[];
  plans: PlanOut[];
}

export interface AnalyticsSummaryOut {
  batch_size: number;
  revenue_at_risk: number;
  revenue_recovered: number;
  recovery_rate: number;
  intervention_cost_total: number;
  net_recovered_revenue: number;
  decision_distribution: Record<string, number>;
  failure_type_breakdown: Record<string, number>;
  baseline_do_nothing: number;
  baseline_blind_retry: number;
  ai_recovered: number;
}
