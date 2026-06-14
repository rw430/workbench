import { RadioTower } from "lucide-react";

import type { EventTimelineItem } from "../types";

type EventTimelineProps = {
  events: EventTimelineItem[];
};

export function EventTimeline({ events }: EventTimelineProps) {
  return (
    <section className="panel timeline-panel" aria-label="Event timeline">
      <div className="panel-heading compact">
        <span className="eyebrow">Events</span>
        <h2>{events.length}</h2>
      </div>
      {events.length === 0 ? (
        <div className="panel-placeholder">
          <RadioTower aria-hidden size={18} />
          <span>No events</span>
        </div>
      ) : (
        <ol className="timeline-list">
          {events.map((event) => (
            <li key={event.id}>
              <strong>{event.event_type}</strong>
              <code>{JSON.stringify(event.payload)}</code>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
