import { useEffect, useMemo, useState } from "react";

import {
  analyzeIntent,
  createProject,
  decideHumanGate,
  eventStreamUrl,
  getProject,
  listAuditLogs,
  recommendAgents,
  startRun,
} from "./api";
import "./App.css";
import { AgentPanel } from "./components/AgentPanel";
import { AuditPanel } from "./components/AuditPanel";
import { DagBoard } from "./components/DagBoard";
import { DeliveryPanel } from "./components/DeliveryPanel";
import { EventTimeline } from "./components/EventTimeline";
import { GoalPanel } from "./components/GoalPanel";
import { HumanGatePanel } from "./components/HumanGatePanel";
import { IntentPanel } from "./components/IntentPanel";
import { RunControl } from "./components/RunControl";
import type {
  AgentSummary,
  AuditLogSummary,
  EventTimelineItem,
  HumanGateDecision,
  IntentAnalysis,
  ProjectState,
  RuntimeEventEnvelope,
} from "./types";

const sampleGoal =
  "请帮我设计一个银行信用卡分期活动配置与审批系统的研发方案，要求支持活动规则配置、审批流、风险校验、灰度发布和操作审计。";

const runtimeEventTypes = [
  "project.created",
  "run.started",
  "task.completed",
  "human_gate.waiting",
  "human_gate.approved",
  "human_gate.rejected",
  "run.completed",
  "run.failed",
];

const stateChangingEventTypes = new Set([
  "run.started",
  "task.completed",
  "human_gate.waiting",
  "human_gate.approved",
  "human_gate.rejected",
  "run.completed",
  "run.failed",
]);

type BusyAction = "analyze" | "create" | "start" | "approve" | "reject" | "audit";

export default function App() {
  const [goal, setGoal] = useState(sampleGoal);
  const [intent, setIntent] = useState<IntentAnalysis | null>(null);
  const [recommendedAgents, setRecommendedAgents] = useState<AgentSummary[]>([]);
  const [projectState, setProjectState] = useState<ProjectState | null>(null);
  const [events, setEvents] = useState<EventTimelineItem[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLogSummary[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [decisionReason, setDecisionReason] = useState("scope confirmed");
  const [busyAction, setBusyAction] = useState<BusyAction | null>(null);
  const [error, setError] = useState<string | null>(null);

  const visibleAgents = useMemo(() => {
    if (recommendedAgents.length) {
      return recommendedAgents;
    }
    return projectState?.agents ?? [];
  }, [projectState?.agents, recommendedAgents]);

  useEffect(() => {
    const tasks = projectState?.tasks ?? [];
    if (tasks.length === 0) {
      setSelectedTaskId(null);
      return;
    }
    if (!selectedTaskId || !tasks.some((task) => task.id === selectedTaskId)) {
      setSelectedTaskId(tasks[0].id);
    }
  }, [projectState?.tasks, selectedTaskId]);

  useEffect(() => {
    if (!projectState?.run.id || typeof EventSource === "undefined") {
      return undefined;
    }

    const source = new EventSource(eventStreamUrl(projectState.run.id));

    function handleEvent(event: MessageEvent<string>) {
      const envelope = JSON.parse(event.data) as RuntimeEventEnvelope;
      setEvents((current) => {
        if (current.some((item) => item.id === envelope.id)) {
          return current;
        }
        return [
          ...current,
          {
            id: envelope.id,
            event_type: envelope.event_type,
            payload: envelope.payload,
            created_at: envelope.created_at,
          },
        ].slice(-30);
      });

      if (
        projectState?.project.id &&
        stateChangingEventTypes.has(envelope.event_type)
      ) {
        void getProject(projectState.project.id)
          .then(setProjectState)
          .catch((err) => {
            setError(err instanceof Error ? err.message : "Refresh failed");
          });
      }
    }

    for (const eventType of runtimeEventTypes) {
      source.addEventListener(eventType, handleEvent);
    }

    source.onerror = () => {
      source.close();
    };

    return () => {
      for (const eventType of runtimeEventTypes) {
        source.removeEventListener(eventType, handleEvent);
      }
      source.close();
    };
  }, [projectState?.run.id, projectState?.project.id]);

  async function runAction(action: BusyAction, callback: () => Promise<void>) {
    setBusyAction(action);
    setError(null);
    try {
      await callback();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Request failed");
    } finally {
      setBusyAction(null);
    }
  }

  async function refreshAudit() {
    const audits = await listAuditLogs("local-user");
    setAuditLogs(audits);
  }

  async function handleAnalyze() {
    await runAction("analyze", async () => {
      const [nextIntent, agents] = await Promise.all([
        analyzeIntent(goal),
        recommendAgents(goal),
      ]);
      setIntent(nextIntent);
      setRecommendedAgents(agents);
    });
  }

  async function handleCreateProject() {
    await runAction("create", async () => {
      const nextState = await createProject(goal);
      setProjectState(nextState);
      setEvents([]);
      await refreshAudit();
    });
  }

  async function handleStartRun() {
    if (!projectState?.run.id) {
      return;
    }
    await runAction("start", async () => {
      const nextState = await startRun(projectState.run.id);
      setProjectState(nextState);
      await refreshAudit();
    });
  }

  async function handleDecision(decision: HumanGateDecision) {
    const gate = projectState?.human_gate;
    if (!gate) {
      return;
    }
    await runAction(decision, async () => {
      const nextState = await decideHumanGate(
        gate.id,
        decision,
        decisionReason,
        "local-user",
      );
      setProjectState(nextState);
      await refreshAudit();
    });
  }

  return (
    <main className="workbench">
      <div className="workspace-shell">
        <aside className="left-rail">
          <GoalPanel
            busy={busyAction !== null}
            canCreateProject={Boolean(intent)}
            goal={goal}
            onAnalyze={handleAnalyze}
            onCreateProject={handleCreateProject}
            onGoalChange={setGoal}
          />
          <IntentPanel intent={intent} />
          <AgentPanel agents={visibleAgents} />
        </aside>

        <section className="main-stage" aria-label="Project workspace">
          <RunControl
            busy={busyAction === "start"}
            onStartRun={handleStartRun}
            state={projectState}
          />
          {error && <p className="error-message">{error}</p>}
          <DagBoard
            onSelectTask={setSelectedTaskId}
            selectedTaskId={selectedTaskId}
            tasks={projectState?.tasks ?? []}
          />
        </section>

        <aside className="right-rail">
          <HumanGatePanel
            busy={busyAction === "approve" || busyAction === "reject"}
            gate={projectState?.human_gate ?? null}
            onApprove={() => handleDecision("approve")}
            onReasonChange={setDecisionReason}
            onReject={() => handleDecision("reject")}
            reason={decisionReason}
          />
          <EventTimeline events={events} />
          <AuditPanel audits={auditLogs} />
        </aside>
      </div>

      <DeliveryPanel
        artifact={projectState?.artifact ?? null}
        lessons={projectState?.lessons ?? []}
        reflection={projectState?.reflection ?? null}
      />
    </main>
  );
}
