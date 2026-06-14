export type IntentAnalysis = {
  mode: string;
  template_id: string;
  domain: string;
  risk_level: string;
  human_gate_required: boolean;
  confidence: number;
  candidate_roles: string[];
};

export type AgentSummary = {
  id: string;
  name: string;
  role: string;
  skills: string[];
  score: number;
  recommendation_reason: string;
};

export type ProjectSummary = {
  id: string;
  goal: string;
  mode: string;
  status: string;
};

export type RoomSummary = {
  id: string;
  project_id: string;
  name: string;
};

export type RunSummary = {
  id: string;
  project_id: string;
  template_id: string;
  status: string;
};

export type TaskSummary = {
  id: string;
  run_id: string;
  node_id: string;
  name: string;
  kind: string;
  role: string;
  depends_on: string[];
  status: string;
  output: string;
  log: string;
};

export type HumanGateSummary = {
  id: string;
  run_id: string;
  task_id: string;
  status: string;
  prompt: string;
};

export type LessonSummary = {
  id: string;
  reflection_id: string;
  category: string;
  content: string;
  confidence: string;
  created_at: string;
};

export type ProjectState = {
  project: ProjectSummary;
  room: RoomSummary;
  agents: AgentSummary[];
  run: RunSummary;
  tasks: TaskSummary[];
  human_gate: HumanGateSummary | null;
  artifact: string | null;
  reflection: string | null;
  lessons: LessonSummary[];
};

export type RuntimeEventEnvelope = {
  id: string;
  run_id: string;
  event_type: string;
  payload: Record<string, unknown>;
  created_at: string;
};

export type EventTimelineItem = {
  id: string;
  event_type: string;
  payload: Record<string, unknown>;
  created_at: string;
};

export type AuditLogSummary = {
  id: string;
  actor_id: string;
  action: string;
  target_type: string;
  target_id: string;
  payload: Record<string, unknown>;
  created_at: string;
};

export type HumanGateDecision = "approve" | "reject";
