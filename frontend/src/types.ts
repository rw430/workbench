export type AgentSummary = {
  id: string;
  name: string;
  role: string;
  skills: string[];
};

export type ProjectSummary = {
  id: number;
  goal: string;
  mode: string;
  status: string;
};

export type RoomSummary = {
  id: number;
  project_id: number;
  name: string;
};

export type RunSummary = {
  id: number;
  project_id: number;
  template_id: string;
  status: string;
};

export type TaskSummary = {
  id: number;
  run_id: number;
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
  id: number;
  run_id: number;
  task_id: number;
  status: string;
  prompt: string;
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
};

export type RuntimeEventSummary = {
  id: string;
  type: string;
  data: string;
};
