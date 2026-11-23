import { Component, computed, input } from '@angular/core';

export type ClaimStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'INFO_REQUESTED' | 'APPROVED' | 'SETTLED' | 'REJECTED' | 'WITHDRAWN';

export const STATUS_LABELS: Record<ClaimStatus, string> = {
  SUBMITTED: 'Submitted',
  UNDER_REVIEW: 'Under review',
  INFO_REQUESTED: 'Info requested',
  APPROVED: 'Approved',
  SETTLED: 'Settled',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
};

/** One colour per claim status. */
@Component({
  selector: 'app-status-badge',
  template: `<span class="badge" [class]="'badge ' + cssClass()">{{ label() }}</span>`,
  styles: `
    .badge {
      display: inline-block; padding: 2px var(--space-2); border-radius: 999px;
      font-size: 0.8rem; font-weight: 600; white-space: nowrap; border: 1px solid transparent;
    }
    .submitted { background: #e8eaf6; color: #303f9f; }
    .under-review { background: var(--accent-soft); color: #1a4fa8; }
    .info-requested { background: var(--warning-soft); color: var(--warning); }
    .approved { background: #e0f2f1; color: #00695c; }
    .settled { background: var(--success-soft); color: var(--success); }
    .rejected { background: var(--danger-soft); color: var(--danger); }
    .withdrawn { background: #eceff1; color: #546e7a; }
  `,
})
export class StatusBadgeComponent {
  readonly status = input.required<ClaimStatus | string | undefined>();
  protected readonly label = computed(() => STATUS_LABELS[this.status() as ClaimStatus] ?? this.status() ?? '');
  protected readonly cssClass = computed(() => (this.status() ?? '').toLowerCase().replace('_', '-'));
}
