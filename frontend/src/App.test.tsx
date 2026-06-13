import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import App from "./App";
import * as api from "./api";
import type { ProjectState } from "./types";

const waitingProjectState: ProjectState = {
  project: {
    id: 1,
    goal: "信用卡分期活动配置与审批系统研发方案",
    mode: "tasks",
    status: "waiting_human",
  },
  room: { id: 1, project_id: 1, name: "信用卡分期活动研发协同室" },
  agents: [
    { id: "agent-pd-1", name: "需求分析分身", role: "PD", skills: ["PRD"] },
    { id: "agent-dev-1", name: "研发实现分身", role: "DEV", skills: ["实现"] },
  ],
  run: {
    id: 1,
    project_id: 1,
    template_id: "credit_card_installment_campaign_v1",
    status: "waiting_human",
  },
  tasks: [
    {
      id: 1,
      run_id: 1,
      node_id: "need_analysis",
      name: "需求分析",
      kind: "llm/prd_draft",
      role: "PD",
      depends_on: [],
      status: "completed",
      output: "PRD 草稿",
      log: "done",
    },
    {
      id: 2,
      run_id: 1,
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
    id: 1,
    run_id: 1,
    task_id: 2,
    status: "waiting",
    prompt: "请确认 PRD 草稿是否可以进入评审。",
  },
  artifact: null,
  reflection: null,
};

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

  emit(type: string, data: string, lastEventId = "1") {
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

describe("Xiaoc workbench", () => {
  it("creates a project and shows the human gate", async () => {
    installFakeEventSource();
    vi.spyOn(api, "createProject").mockResolvedValue(waitingProjectState);

    render(<App />);
    await userEvent.clear(screen.getByLabelText("目标输入"));
    await userEvent.type(
      screen.getByLabelText("目标输入"),
      "信用卡分期活动配置与审批系统研发方案",
    );
    await userEvent.click(screen.getByRole("button", { name: "启动任务" }));

    expect(await screen.findByText("tasks")).toBeInTheDocument();
    expect(screen.getByText("credit_card_installment_campaign_v1")).toBeInTheDocument();
    expect(screen.getByText("需求分析分身")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "确认 HumanGate" })).toBeInTheDocument();
  });

  it("subscribes to persisted SSE events and renders the event stream", async () => {
    installFakeEventSource();
    vi.spyOn(api, "createProject").mockResolvedValue(waitingProjectState);

    render(<App />);
    await userEvent.click(screen.getByRole("button", { name: "启动任务" }));
    await screen.findByText("tasks");

    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.instances[0].url).toContain(
      "/api/events/stream?run_id=1",
    );

    FakeEventSource.instances[0].emit("run.created", '{"project_id":1}');

    expect(await screen.findByText("事件流")).toBeInTheDocument();
    expect(screen.getByText("run.created")).toBeInTheDocument();
    expect(screen.getByText('{"project_id":1}')).toBeInTheDocument();
  });
});
