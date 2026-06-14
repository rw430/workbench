import { CheckCircle2, CircleDashed, ShieldCheck } from "lucide-react";

import type { TaskSummary } from "../types";

type DagBoardProps = {
  tasks: TaskSummary[];
  selectedTaskId: string | null;
  onSelectTask: (taskId: string) => void;
};

function taskIcon(task: TaskSummary) {
  if (task.status === "completed") {
    return <CheckCircle2 aria-hidden size={16} />;
  }
  if (task.kind === "human_gate") {
    return <ShieldCheck aria-hidden size={16} />;
  }
  return <CircleDashed aria-hidden size={16} />;
}

export function DagBoard({ tasks, selectedTaskId, onSelectTask }: DagBoardProps) {
  const selectedTask = tasks.find((task) => task.id === selectedTaskId) ?? tasks[0];

  return (
    <section className="panel dag-panel" aria-label="DAG board">
      <div className="panel-heading compact">
        <span className="eyebrow">DAG</span>
        <h2>{tasks.length ? `${tasks.length} tasks` : "Pending"}</h2>
      </div>
      <div className="task-grid">
        {tasks.map((task) => (
          <button
            className={`task-tile ${task.status} ${
              task.id === selectedTask?.id ? "selected" : ""
            }`}
            key={task.id}
            onClick={() => onSelectTask(task.id)}
            type="button"
          >
            <span className="task-topline">
              {taskIcon(task)}
              <span>{task.role}</span>
            </span>
            <strong>{task.name}</strong>
            <span className="task-status">{task.status}</span>
          </button>
        ))}
      </div>
      {selectedTask && (
        <article className="task-output" aria-label="Selected task output">
          <div className="row-title">
            <strong>{selectedTask.name}</strong>
            <span className="role-pill">{selectedTask.node_id}</span>
          </div>
          <pre>{selectedTask.output || selectedTask.log || "No output"}</pre>
        </article>
      )}
    </section>
  );
}
