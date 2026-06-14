import { Check, X } from "lucide-react";

import type { HumanGateSummary } from "../types";

type HumanGatePanelProps = {
  gate: HumanGateSummary | null;
  reason: string;
  busy: boolean;
  onReasonChange: (value: string) => void;
  onApprove: () => void;
  onReject: () => void;
};

export function HumanGatePanel({
  gate,
  reason,
  busy,
  onReasonChange,
  onApprove,
  onReject,
}: HumanGatePanelProps) {
  const waiting = gate?.status === "waiting";

  return (
    <section className="panel human-gate-panel" aria-label="HumanGate">
      <div className="panel-heading compact">
        <span className="eyebrow">HumanGate</span>
        <h2>{gate?.status ?? "Pending"}</h2>
      </div>
      {gate ? (
        <>
          <p className="gate-prompt">{gate.prompt}</p>
          <label className="field compact-field">
            <span>Decision reason</span>
            <textarea
              disabled={busy || !waiting}
              onChange={(event) => onReasonChange(event.target.value)}
              rows={3}
              value={reason}
            />
          </label>
          <div className="action-row">
            <button disabled={busy || !waiting} onClick={onApprove} type="button">
              <Check aria-hidden size={16} />
              Approve
            </button>
            <button
              className="danger-action"
              disabled={busy || !waiting}
              onClick={onReject}
              type="button"
            >
              <X aria-hidden size={16} />
              Reject
            </button>
          </div>
        </>
      ) : (
        <div className="panel-placeholder">
          <ShieldIcon />
          <span>No gate</span>
        </div>
      )}
    </section>
  );
}

function ShieldIcon() {
  return <Check aria-hidden size={18} />;
}
