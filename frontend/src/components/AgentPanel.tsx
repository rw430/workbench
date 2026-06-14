import { UsersRound } from "lucide-react";

import type { AgentSummary } from "../types";

type AgentPanelProps = {
  agents: AgentSummary[];
};

export function AgentPanel({ agents }: AgentPanelProps) {
  return (
    <section className="panel" aria-label="Recommended agents">
      <div className="panel-heading compact">
        <span className="eyebrow">Agents</span>
        <h2>{agents.length ? `${agents.length} selected` : "Pending"}</h2>
      </div>
      {agents.length === 0 ? (
        <div className="panel-placeholder">
          <UsersRound aria-hidden size={18} />
          <span>No agents</span>
        </div>
      ) : (
        <div className="agent-list">
          {agents.map((agent) => (
            <article className="agent-row" key={agent.id}>
              <div>
                <div className="row-title">
                  <strong>{agent.name}</strong>
                  <span className="role-pill">{agent.role}</span>
                </div>
                <p>{agent.recommendation_reason}</p>
                <span className="muted">{agent.skills.join(" / ")}</span>
              </div>
              <span className="score">{Math.round(agent.score * 100)}</span>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
