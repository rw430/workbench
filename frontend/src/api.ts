import type {
  AgentSummary,
  AuditLogSummary,
  HumanGateDecision,
  IntentAnalysis,
  ProjectState,
} from "./types";

const API_BASE = import.meta.env.VITE_API_BASE ?? "http://127.0.0.1:8889";

async function parseJsonResponse<T>(
  response: Response,
  action: string,
): Promise<T> {
  if (!response.ok) {
    throw new Error(`${action} failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export async function analyzeIntent(goal: string): Promise<IntentAnalysis> {
  const response = await fetch(`${API_BASE}/api/intent/analyze`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ goal }),
  });
  return parseJsonResponse<IntentAnalysis>(response, "Analyze intent");
}

export async function recommendAgents(goal: string): Promise<AgentSummary[]> {
  const response = await fetch(`${API_BASE}/api/agents/recommend`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ goal }),
  });
  return parseJsonResponse<AgentSummary[]>(response, "Recommend agents");
}

export async function createProject(goal: string): Promise<ProjectState> {
  const response = await fetch(`${API_BASE}/api/projects`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ goal }),
  });
  return parseJsonResponse<ProjectState>(response, "Create project");
}

export async function getProject(projectId: string): Promise<ProjectState> {
  const response = await fetch(`${API_BASE}/api/projects/${projectId}`);
  return parseJsonResponse<ProjectState>(response, "Get project");
}

export async function startRun(runId: string): Promise<ProjectState> {
  const response = await fetch(`${API_BASE}/api/runs/${runId}/start`, {
    method: "POST",
  });
  return parseJsonResponse<ProjectState>(response, "Start run");
}

export async function decideHumanGate(
  gateId: string,
  decision: HumanGateDecision,
  reason: string,
  decidedBy = "local-user",
): Promise<ProjectState> {
  const response = await fetch(
    `${API_BASE}/api/human-gates/${gateId}/${decision}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ reason, decided_by: decidedBy }),
    },
  );
  return parseJsonResponse<ProjectState>(response, `Human gate ${decision}`);
}

export async function listAuditLogs(
  actorId = "local-user",
): Promise<AuditLogSummary[]> {
  const params = new URLSearchParams({ actor_id: actorId });
  const response = await fetch(`${API_BASE}/api/audit-logs?${params.toString()}`);
  return parseJsonResponse<AuditLogSummary[]>(response, "List audit logs");
}

export function eventStreamUrl(runId: string): string {
  const params = new URLSearchParams({ run_id: runId });
  return `${API_BASE}/api/events/stream?${params.toString()}`;
}
