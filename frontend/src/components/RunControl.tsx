import { Play, RotateCw } from "lucide-react";

import type { ProjectState } from "../types";

type RunControlProps = {
  state: ProjectState | null;
  busy: boolean;
  onStartRun: () => void;
};

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    created: "Created",
    pending: "Pending",
    running: "Running",
    waiting_human: "Waiting Human",
    completed: "Completed",
    failed: "Failed",
  };
  return labels[status] ?? status;
}

export function RunControl({ state, busy, onStartRun }: RunControlProps) {
  const canStart = Boolean(state?.run.id) && state?.run.status === "created";
  const completedCount =
    state?.tasks.filter((task) => task.status === "completed").length ?? 0;

  return (
    <section className="panel run-control" aria-label="Run control">
      <div className="run-summary">
        <div>
          <span>Project</span>
          <strong>{state?.project.status ? statusLabel(state.project.status) : "Pending"}</strong>
        </div>
        <div>
          <span>Run</span>
          <strong>{state?.run.status ? statusLabel(state.run.status) : "Pending"}</strong>
        </div>
        <div>
          <span>Template</span>
          <strong>{state?.run.template_id ?? "none"}</strong>
        </div>
        <div>
          <span>Progress</span>
          <strong>
            {completedCount}/{state?.tasks.length ?? 0}
          </strong>
        </div>
      </div>
      <button
        className="primary-action"
        disabled={busy || !canStart}
        onClick={onStartRun}
        type="button"
      >
        {busy ? <RotateCw aria-hidden size={16} /> : <Play aria-hidden size={16} />}
        Start Run
      </button>
    </section>
  );
}
