import { Rocket, Search } from "lucide-react";

type GoalPanelProps = {
  goal: string;
  busy: boolean;
  onGoalChange: (value: string) => void;
  onAnalyze: () => void;
  onCreateProject: () => void;
  canCreateProject: boolean;
};

export function GoalPanel({
  goal,
  busy,
  onGoalChange,
  onAnalyze,
  onCreateProject,
  canCreateProject,
}: GoalPanelProps) {
  return (
    <section className="panel goal-panel" aria-label="Goal">
      <div className="panel-heading">
        <span className="eyebrow">Goal</span>
        <h1>Xiaoc Workbench</h1>
      </div>
      <label className="field">
        <span>Goal</span>
        <textarea
          value={goal}
          onChange={(event) => onGoalChange(event.target.value)}
          rows={8}
        />
      </label>
      <div className="action-row">
        <button
          className="secondary-action"
          disabled={busy || !goal.trim()}
          onClick={onAnalyze}
          type="button"
        >
          <Search aria-hidden size={16} />
          Analyze
        </button>
        <button
          className="primary-action"
          disabled={busy || !canCreateProject}
          onClick={onCreateProject}
          type="button"
        >
          <Rocket aria-hidden size={16} />
          Create Project
        </button>
      </div>
    </section>
  );
}
