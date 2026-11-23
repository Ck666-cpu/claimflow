import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { HistoryItem } from '../api/models/history-item';
import { userName } from '../core/users';
import { StatusBadgeComponent } from './status-badge.component';

/** The claim's status history, oldest first (newest last), with actor names from the demo users. */
@Component({
  selector: 'app-claim-timeline',
  imports: [DatePipe, StatusBadgeComponent],
  template: `
    <ol class="timeline">
      @for (entry of history(); track $index) {
        <li>
          <div class="line">
            <app-status-badge [status]="entry.toStatus" />
            <span class="muted">by {{ actor(entry.actorId) }} · {{ entry.occurredAt | date: 'd MMM y, HH:mm' }}</span>
          </div>
          @if (entry.reason) {
            <div class="reason">{{ readable(entry.reason) }}</div>
          }
        </li>
      } @empty {
        <li class="muted">No history yet.</li>
      }
    </ol>
  `,
  styles: `
    .timeline { list-style: none; margin: 0; padding: 0 0 0 var(--space-3); border-left: 2px solid var(--border); }
    li { position: relative; padding: 0 0 var(--space-3) var(--space-3); }
    li::before {
      content: ''; position: absolute; left: calc(-1 * var(--space-3) - 6px); top: 6px;
      width: 10px; height: 10px; border-radius: 50%; background: var(--accent);
    }
    .line { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); }
    .reason { margin-top: var(--space-1); white-space: pre-line; }
  `,
})
export class ClaimTimelineComponent {
  readonly history = input<HistoryItem[] | undefined>([]);
  protected readonly actor = userName;

  /** History reasons can mention user ids (e.g. "Assigned to <id>"): show names instead. */
  protected readable(reason: string): string {
    return reason.replace(UUID, (id) => userName(id));
  }
}

const UUID = /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi;
