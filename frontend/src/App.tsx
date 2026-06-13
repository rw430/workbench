import {
  CheckCircle2,
  CircleDashed,
  ClipboardCheck,
  FileText,
  Play,
  ShieldCheck,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { confirmHumanGate, createProject, eventStreamUrl } from "./api";
import type { ProjectState, RuntimeEventSummary, TaskSummary } from "./types";
import "./App.css";

const sampleGoal =
  "请帮我设计一个银行信用卡分期活动配置与审批系统的研发方案，要求支持活动规则配置、审批流、风险校验、灰度发布和操作审计。";

function statusText(status: string): string {
  const labels: Record<string, string> = {
    pending: "待执行",
    running: "执行中",
    waiting_human: "等待确认",
    completed: "已完成",
    failed: "失败",
  };
  return labels[status] ?? status;
}

function taskIcon(task: TaskSummary) {
  if (task.status === "completed") {
    return <CheckCircle2 aria-hidden size={16} />;
  }
  if (task.kind === "human_gate") {
    return <ShieldCheck aria-hidden size={16} />;
  }
  return <CircleDashed aria-hidden size={16} />;
}

export default function App() {
  const [goal, setGoal] = useState(sampleGoal);
  const [state, setState] = useState<ProjectState | null>(null);
  const [events, setEvents] = useState<RuntimeEventSummary[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const completedCount = useMemo(
    () => state?.tasks.filter((task) => task.status === "completed").length ?? 0,
    [state],
  );

  async function start() {
    setBusy(true);
    setError(null);
    setEvents([]);
    try {
      setState(await createProject(goal));
    } catch (err) {
      setError(err instanceof Error ? err.message : "启动失败");
    } finally {
      setBusy(false);
    }
  }

  async function confirmGate() {
    if (!state?.human_gate) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      setState(await confirmHumanGate(state.human_gate.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "确认失败");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    if (!state || typeof EventSource === "undefined") {
      return undefined;
    }

    const runStatus = state.run.status;
    const source = new EventSource(eventStreamUrl(state.run.id));
    const eventTypes = [
      "run.created",
      "run.running",
      "run.task.running",
      "run.task.completed",
      "human_gate.waiting",
      "human_gate.confirmed",
      "run.completed",
      "run.failed",
    ];

    function handleEvent(event: MessageEvent<string>) {
      const id = event.lastEventId || `${event.type}:${event.data}`;
      setEvents((current) => {
        if (current.some((candidate) => candidate.id === id)) {
          return current;
        }
        return [...current, { id, type: event.type, data: event.data }].slice(-20);
      });

      if (
        event.type === "run.completed" ||
        event.type === "run.failed" ||
        (runStatus === "waiting_human" && event.type === "human_gate.waiting")
      ) {
        source.close();
      }
    }

    for (const eventType of eventTypes) {
      source.addEventListener(eventType, handleEvent);
    }

    source.onerror = () => {
      source.close();
    };

    return () => {
      for (const eventType of eventTypes) {
        source.removeEventListener(eventType, handleEvent);
      }
      source.close();
    };
  }, [state?.run.id, state?.run.status]);

  return (
    <main className="workbench">
      <section className="command-bar" aria-label="任务启动">
        <div className="brand-block">
          <span className="eyebrow">Xiaoc Workbench MVP</span>
          <h1>小IC工作台</h1>
        </div>
        <label className="goal-field">
          <span>目标输入</span>
          <textarea
            value={goal}
            onChange={(event) => setGoal(event.target.value)}
            rows={3}
          />
        </label>
        <button
          className="primary-action"
          onClick={start}
          disabled={busy || !goal.trim()}
          type="button"
        >
          <Play aria-hidden size={16} />
          启动任务
        </button>
      </section>

      {error && <p className="error-message">{error}</p>}

      {!state && (
        <section className="empty-state" aria-label="当前状态">
          <ClipboardCheck aria-hidden size={28} />
          <p>输入目标后启动任务，系统会创建协同室、推荐 Agent 并暂停在 HumanGate。</p>
        </section>
      )}

      {state && (
        <section className="workspace-grid" aria-label="项目工作台">
          <div className="run-strip">
            <div>
              <span className="meta-label">模式</span>
              <strong>{state.project.mode}</strong>
            </div>
            <div>
              <span className="meta-label">模板</span>
              <strong>{state.run.template_id}</strong>
            </div>
            <div>
              <span className="meta-label">运行状态</span>
              <strong>{statusText(state.run.status)}</strong>
            </div>
            <div>
              <span className="meta-label">进度</span>
              <strong>
                {completedCount}/{state.tasks.length}
              </strong>
            </div>
          </div>

          <aside className="agent-rail" aria-label="Agent Team">
            <div className="section-heading">
              <h2>Agent Team</h2>
              <span>{state.room.name}</span>
            </div>
            <div className="agent-list">
              {state.agents.map((agent) => (
                <article className="agent-row" key={agent.id}>
                  <div>
                    <strong>{agent.name}</strong>
                    <span>{agent.skills.join(" / ")}</span>
                  </div>
                  <span className="role-pill">{agent.role}</span>
                </article>
              ))}
            </div>
          </aside>

          <section className="dag-board" aria-label="OMA DAG">
            <div className="section-heading">
              <h2>OMA DAG</h2>
              <span>{state.tasks.length} 个节点</span>
            </div>
            <div className="task-grid">
              {state.tasks.map((task) => (
                <article className={`task-tile ${task.status}`} key={task.id}>
                  <div className="task-topline">
                    {taskIcon(task)}
                    <span>{task.role}</span>
                  </div>
                  <strong>{task.name}</strong>
                  <span className="task-status">{statusText(task.status)}</span>
                  {task.output && <p>{task.output}</p>}
                </article>
              ))}
            </div>
          </section>

          <section className="event-stream" aria-label="事件流">
            <div className="section-heading">
              <h2>事件流</h2>
              <span>{events.length} 条事件</span>
            </div>
            {events.length === 0 ? (
              <p className="stream-empty">等待后端 SSE 事件。</p>
            ) : (
              <ol>
                {events.map((event) => (
                  <li key={event.id}>
                    <strong>{event.type}</strong>
                    <code>{event.data}</code>
                  </li>
                ))}
              </ol>
            )}
          </section>

          {state.human_gate?.status === "waiting" && (
            <section className="human-gate" aria-label="HumanGate">
              <div>
                <h2>HumanGate</h2>
                <p>{state.human_gate.prompt}</p>
              </div>
              <button onClick={confirmGate} disabled={busy} type="button">
                <ShieldCheck aria-hidden size={16} />
                确认 HumanGate
              </button>
            </section>
          )}

          {(state.artifact || state.reflection) && (
            <section className="delivery-board" aria-label="交付结果">
              {state.artifact && (
                <article>
                  <h2>
                    <FileText aria-hidden size={18} />
                    交付物
                  </h2>
                  <p>{state.artifact}</p>
                </article>
              )}
              {state.reflection && (
                <article>
                  <h2>
                    <ClipboardCheck aria-hidden size={18} />
                    REFLECTION
                  </h2>
                  <pre>{state.reflection}</pre>
                </article>
              )}
            </section>
          )}
        </section>
      )}
    </main>
  );
}
