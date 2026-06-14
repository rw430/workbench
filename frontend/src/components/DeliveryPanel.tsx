import { BookOpenCheck, FileText, Lightbulb } from "lucide-react";

import type { LessonSummary } from "../types";

type DeliveryPanelProps = {
  artifact: string | null;
  reflection: string | null;
  lessons: LessonSummary[];
};

export function DeliveryPanel({ artifact, reflection, lessons }: DeliveryPanelProps) {
  return (
    <section className="delivery-strip" aria-label="Delivery">
      <article className="panel delivery-panel">
        <div className="panel-heading compact">
          <span className="eyebrow">Artifact</span>
          <h2>
            <FileText aria-hidden size={16} />
            Output
          </h2>
        </div>
        <pre>{artifact ?? "No artifact"}</pre>
      </article>
      <article className="panel delivery-panel">
        <div className="panel-heading compact">
          <span className="eyebrow">Reflection</span>
          <h2>
            <BookOpenCheck aria-hidden size={16} />
            Review
          </h2>
        </div>
        <pre>{reflection ?? "No reflection"}</pre>
      </article>
      <article className="panel delivery-panel">
        <div className="panel-heading compact">
          <span className="eyebrow">Lessons</span>
          <h2>
            <Lightbulb aria-hidden size={16} />
            {lessons.length}
          </h2>
        </div>
        {lessons.length === 0 ? (
          <p className="muted">No lessons</p>
        ) : (
          <ul className="lesson-list">
            {lessons.map((lesson) => (
              <li key={lesson.id}>
                <strong>{lesson.category}</strong>
                <span>{lesson.content}</span>
              </li>
            ))}
          </ul>
        )}
      </article>
    </section>
  );
}
