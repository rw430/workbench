import { ClipboardList } from "lucide-react";

import type { AuditLogSummary } from "../types";

type AuditPanelProps = {
  audits: AuditLogSummary[];
};

export function AuditPanel({ audits }: AuditPanelProps) {
  return (
    <section className="panel audit-panel" aria-label="Audit logs">
      <div className="panel-heading compact">
        <span className="eyebrow">Audit</span>
        <h2>{audits.length}</h2>
      </div>
      {audits.length === 0 ? (
        <div className="panel-placeholder">
          <ClipboardList aria-hidden size={18} />
          <span>No audits</span>
        </div>
      ) : (
        <ol className="audit-list">
          {audits.map((audit) => (
            <li key={audit.id}>
              <strong>{audit.action}</strong>
              <span>
                {audit.target_type}:{audit.target_id}
              </span>
              <code>{JSON.stringify(audit.payload)}</code>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
