import type { ProjectState } from "./types";

const API_BASE = import.meta.env.VITE_API_BASE ?? "http://127.0.0.1:8889";

async function parseProjectResponse(
  response: Response,
  action: string,
): Promise<ProjectState> {
  if (!response.ok) {
    throw new Error(`${action} failed: ${response.status}`);
  }
  return response.json();
}

export async function createProject(goal: string): Promise<ProjectState> {
  const response = await fetch(`${API_BASE}/api/projects`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ goal }),
  });
  return parseProjectResponse(response, "Create project");
}

export async function confirmHumanGate(gateId: number): Promise<ProjectState> {
  const response = await fetch(`${API_BASE}/api/human-gates/${gateId}/confirm`, {
    method: "POST",
  });
  return parseProjectResponse(response, "Confirm gate");
}

export function eventStreamUrl(runId: number): string {
  return `${API_BASE}/api/events/stream?run_id=${runId}`;
}
