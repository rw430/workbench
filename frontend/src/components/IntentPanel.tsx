import { Gauge, ShieldCheck } from "lucide-react";

import type { IntentAnalysis } from "../types";

type IntentPanelProps = {
  intent: IntentAnalysis | null;
};

export function IntentPanel({ intent }: IntentPanelProps) {
  return (
    <section className="panel" aria-label="Intent analysis">
      <div className="panel-heading compact">
        <span className="eyebrow">Intent</span>
        <h2>{intent ? intent.template_id : "Pending"}</h2>
      </div>
      {intent ? (
        <div className="metric-grid">
          <div>
            <span>Domain</span>
            <strong>{intent.domain}</strong>
          </div>
          <div>
            <span>Risk</span>
            <strong>{intent.risk_level}</strong>
          </div>
          <div>
            <span>Confidence</span>
            <strong>{Math.round(intent.confidence * 100)}%</strong>
          </div>
          <div>
            <span>HumanGate</span>
            <strong>{intent.human_gate_required ? "Required" : "Optional"}</strong>
          </div>
        </div>
      ) : (
        <div className="panel-placeholder">
          <Gauge aria-hidden size={18} />
          <span>Not analyzed</span>
        </div>
      )}
      {intent && (
        <div className="role-strip" aria-label="Candidate roles">
          <ShieldCheck aria-hidden size={16} />
          {intent.candidate_roles.map((role) => (
            <span className="role-pill" key={role}>
              {role}
            </span>
          ))}
        </div>
      )}
    </section>
  );
}
