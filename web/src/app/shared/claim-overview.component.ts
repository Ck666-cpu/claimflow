import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { ClaimDetail } from '../api/models/claim-detail';
import { CLAIM_TYPE_LABELS, marketName } from '../core/markets';
import { userName } from '../core/users';
import { MoneyPipe } from './money.pipe';
import { RelativeDaysPipe } from './relative-days.pipe';
import { StatusBadgeComponent } from './status-badge.component';

/**
 * Claim header and incident details, shared by the claimant and staff pages. The reserve is shown
 * only when {@code showReserve} is set (staff); the API returns null for claimants anyway.
 */
@Component({
  selector: 'app-claim-overview',
  imports: [DatePipe, MoneyPipe, RelativeDaysPipe, StatusBadgeComponent],
  template: `
    @let c = claim();
    <div class="card">
      <div class="header">
        <h1>{{ c.referenceNo }}</h1>
        <app-status-badge [status]="c.status" />
        @if (c.slaBreached && showReserve()) {
          <span class="breach">SLA breached</span>
        }
      </div>

      <dl class="facts">
        <div><dt>Claimed</dt><dd>{{ c.claimedAmount | money: c.currency }}</dd></div>
        @if (c.approvedAmount != null) {
          <div><dt>Approved</dt><dd>{{ c.approvedAmount | money: c.currency }}</dd></div>
        }
        @if (showReserve() && c.reserveAmount != null) {
          <div><dt>Reserve</dt><dd>{{ c.reserveAmount | money: c.currency }}</dd></div>
        }
        <div><dt>Type</dt><dd>{{ types[c.type ?? ''] }}</dd></div>
        <div><dt>Market</dt><dd>{{ market(c.marketCode) }} ({{ c.currency }})</dd></div>
        <div><dt>Policy</dt><dd>{{ c.policyNumber }}</dd></div>
        <div><dt>Incident date</dt><dd>{{ c.incidentDate | date: 'd MMM y' }}</dd></div>
        <div><dt>Submitted</dt><dd>{{ c.submittedAt | date: 'd MMM y' }} ({{ c.submittedAt | relativeDays }})</dd></div>
        @if (showReserve()) {
          <div><dt>Claimant</dt><dd>{{ user(c.claimantId) }}</dd></div>
          <div><dt>Assigned to</dt><dd>{{ c.assigneeId ? user(c.assigneeId) : 'Unassigned' }}</dd></div>
        }
      </dl>

      @if (c.status === 'REJECTED' && c.rejectionReason) {
        <div class="rejection"><strong>Reason for rejection:</strong> {{ c.rejectionReason }}</div>
      }

      <h2>What happened</h2>
      <p class="description">{{ c.description }}</p>
    </div>
  `,
  styles: `
    .header { display: flex; align-items: center; gap: var(--space-3); margin-bottom: var(--space-3); }
    .header h1 { margin: 0; }
    .facts { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: var(--space-3); margin: 0 0 var(--space-4); }
    dt { font-size: 0.8rem; color: var(--text-muted); }
    dd { margin: 0; font-weight: 500; }
    .rejection { padding: var(--space-3); margin-bottom: var(--space-4); border-radius: var(--radius); background: var(--danger-soft); }
    .description { white-space: pre-line; margin: 0; }
  `,
})
export class ClaimOverviewComponent {
  readonly claim = input.required<ClaimDetail>();
  readonly showReserve = input(false);
  protected readonly types = CLAIM_TYPE_LABELS;
  protected readonly market = marketName;
  protected readonly user = userName;
}
