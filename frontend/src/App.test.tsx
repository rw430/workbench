import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import App from "./App";
import * as api from "./api";
import type {
  AgentSummary,
  AuditLogSummary,
  IntentAnalysis,
  ProjectState,
} from "./types";

const intent: IntentAnalysis = {
  mode: "tasks",
  template_id: "credit_card_installment_campaign_v1",
  domain: "banking_credit_card",
  risk_level: "medium",
  human_gate_required: true,
  confidence: 0.92,
  candidate_roles: ["PD", "DEV", "QA", "RISK", "PMO"],
};

const agents: AgentSummary[] = [
  {
    id: "agent-pd",
    name: "需求分析分身",
    role: "PD",
    skills: ["PRD", "范围澄清"],
    score: 0.96,
    recommendation_reason: "负责需求分析和 PRD 范围确认。",
  },
  {
    id: "agent-risk",
    name: "风险合规分身",
    role: "RISK",
    skills: ["合规", "审计"],
    score: 0.91,
    recommendation_reason: "负责银行业务边界和审计要求。",
  },
];

const waitingProjectState: ProjectState = {
  project: {
    id: "project-1",
    goal: "信用卡分期活动配置与审批系统研发方案",
    mode: "tasks",
    status: "waiting_human",
  },
  room: { id: "room-1", project_id: "project-1", name: "信用卡分期活动研发协同室" },
  agents,
  run: {
    id: "run-1",
    project_id: "project-1",
    template_id: "credit_card_installment_campaign_v1",
    status: "waiting_human",
  },
  tasks: [
    {
      id: "task-1",
      run_id: "run-1",
      node_id: "need_analysis",
      name: "需求分析",
      kind: "llm_prd_draft",
      role: "PD",
      depends_on: [],
      status: "completed",
      output: "PRD 草稿",
      log: "done",
    },
    {
      id: "task-2",
      run_id: "run-1",
      node_id: "human_gate_prd",
      name: "PRD 确认",
      kind: "human_gate",
      role: "USER",
      depends_on: ["need_analysis"],
      status: "waiting_human",
      output: "",
      log: "waiting",
    },
  ],
  human_gate: {
    id: "gate-1",
    run_id: "run-1",
    task_id: "task-2",
    status: "waiting",
    prompt: "Confirm PRD scope before risk review.",
  },
  artifact: null,
  reflection: null,
  lessons: [],
};

const completedProjectState: ProjectState = {
  ...waitingProjectState,
  project: { ...waitingProjectState.project, status: "completed" },
  run: { ...waitingProjectState.run, status: "completed" },
  human_gate: { ...waitingProjectState.human_gate!, status: "approved" },
  tasks: waitingProjectState.tasks.map((task) => ({ ...task, status: "completed" })),
  artifact: "信用卡分期活动研发交付物\n任务输出",
  reflection: "复盘\n后续可以接入真实 LLM。",
  lessons: [
    {
      id: "lesson-1",
      reflection_id: "reflection-1",
      category: "scope_control",
      content: "HumanGate 减少返工。",
      confidence: "high",
      created_at: "2026-06-14T00:00:00Z",
    },
  ],
};

const audits: AuditLogSummary[] = [
  {
    id: "audit-1",
    actor_id: "local-user",
    action: "HUMAN_GATE_APPROVE",
    target_type: "human_gate",
    target_id: "gate-1",
    payload: { reason: "scope confirmed" },
    created_at: "2026-06-14T00:00:00Z",
  },
];

type EventCallback = (event: MessageEvent<string>) => void;

class FakeEventSource {
  static instances: FakeEventSource[] = [];

  readonly url: string;
  readonly close = vi.fn();
  private readonly listeners = new Map<string, EventCallback[]>();

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, callback: EventCallback) {
    const callbacks = this.listeners.get(type) ?? [];
    callbacks.push(callback);
    this.listeners.set(type, callbacks);
  }

  removeEventListener(type: string, callback: EventCallback) {
    const callbacks = this.listeners.get(type) ?? [];
    this.listeners.set(
      type,
      callbacks.filter((candidate) => candidate !== callback),
    );
  }

  emit(type: string, data: string, lastEventId = "event-1") {
    const event = new MessageEvent(type, { data, lastEventId });
    for (const callback of this.listeners.get(type) ?? []) {
      callback(event);
    }
  }
}

function installFakeEventSource() {
  FakeEventSource.instances = [];
  vi.stubGlobal("EventSource", FakeEventSource);
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("Phase 5 workbench", () => {
  it("runs the golden path through intent, agents, project, start, approve, audit, and delivery", async () => {
    installFakeEventSource();
    vi.spyOn(api, "analyzeIntent").mockResolvedValue(intent);
    vi.spyOn(api, "recommendAgents").mockResolvedValue(agents);
    vi.spyOn(api, "createProject").mockResolvedValue({
      ...waitingProjectState,
      project: { ...waitingProjectState.project, status: "created" },
      run: { ...waitingProjectState.run, status: "created" },
      human_gate: null,
    });
    vi.spyOn(api, "startRun").mockResolvedValue(waitingProjectState);
    vi.spyOn(api, "decideHumanGate").mockResolvedValue(completedProjectState);
    vi.spyOn(api, "listAuditLogs").mockResolvedValue(audits);

    render(<App />);
    await userEvent.click(screen.getByRole("button", { name: /Analyze/i }));

    expect(await screen.findByText("banking_credit_card")).toBeInTheDocument();
    expect(screen.getByText("需求分析分身")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Create Project/i }));
    expect(await screen.findAllByText("credit_card_installment_campaign_v1")).not.toHaveLength(0);

    await userEvent.click(screen.getByRole("button", { name: /Start Run/i }));
    expect(await screen.findByText("Confirm PRD scope before risk review.")).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText("Decision reason"));
    await userEvent.type(screen.getByLabelText("Decision reason"), "scope confirmed");
    await userEvent.click(screen.getByRole("button", { name: /Approve/i }));

    expect(await screen.findByText(/信用卡分期活动研发交付物/)).toBeInTheDocument();
    expect(screen.getByText("HUMAN_GATE_APPROVE")).toBeInTheDocument();
    expect(screen.getByText("HumanGate 减少返工。")).toBeInTheDocument();
  });

  it("subscribes to Phase 4 SSE envelopes and renders the event timeline", async () => {
    installFakeEventSource();
    vi.spyOn(api, "analyzeIntent").mockResolvedValue(intent);
    vi.spyOn(api, "recommendAgents").mockResolvedValue(agents);
    vi.spyOn(api, "createProject").mockResolvedValue(waitingProjectState);
    vi.spyOn(api, "listAuditLogs").mockResolvedValue([]);

    render(<App />);
    await userEvent.click(screen.getByRole("button", { name: /Analyze/i }));
    await userEvent.click(await screen.findByRole("button", { name: /Create Project/i }));

    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.instances[0].url).toContain(
      "/api/events/stream?run_id=run-1",
    );

    FakeEventSource.instances[0].emit(
      "task.completed",
      JSON.stringify({
        id: "event-1",
        run_id: "run-1",
        event_type: "task.completed",
        payload: { node_id: "need_analysis" },
        created_at: "2026-06-14T00:00:00Z",
      }),
    );

    expect(await screen.findByText("task.completed")).toBeInTheDocument();
    expect(screen.getAllByText(/need_analysis/)).not.toHaveLength(0);
  });

  it("sends reject decisions to the backend", async () => {
    installFakeEventSource();
    vi.spyOn(api, "analyzeIntent").mockResolvedValue(intent);
    vi.spyOn(api, "recommendAgents").mockResolvedValue(agents);
    vi.spyOn(api, "createProject").mockResolvedValue(waitingProjectState);
    const decide = vi.spyOn(api, "decideHumanGate").mockResolvedValue({
      ...waitingProjectState,
      project: { ...waitingProjectState.project, status: "failed" },
      run: { ...waitingProjectState.run, status: "failed" },
      human_gate: { ...waitingProjectState.human_gate!, status: "rejected" },
    });
    vi.spyOn(api, "listAuditLogs").mockResolvedValue([]);

    render(<App />);
    await userEvent.click(screen.getByRole("button", { name: /Analyze/i }));
    await userEvent.click(await screen.findByRole("button", { name: /Create Project/i }));
    await userEvent.clear(await screen.findByLabelText("Decision reason"));
    await userEvent.type(screen.getByLabelText("Decision reason"), "scope too broad");
    await userEvent.click(screen.getByRole("button", { name: /Reject/i }));

    await waitFor(() => {
      expect(decide).toHaveBeenCalledWith(
        "gate-1",
        "reject",
        "scope too broad",
        "local-user",
      );
    });
  });
});
