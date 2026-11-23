import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { ExposureReport } from '../../api/models/exposure-report';
import { MarketExposure } from '../../api/models/market-exposure';
import { WorkloadReport } from '../../api/models/workload-report';
import { ClaimsApi } from '../../core/claims-api.service';
import { pollWhileVisible } from '../../core/polling';
import { SessionService } from '../../core/session.service';
import { MoneyPipe } from '../../shared/money.pipe';

const AGE_BUCKETS = ['0-7d', '8-30d', '31d+'] as const;

/**
 * Team dashboard from GET /api/v1/metrics/workload (officers and leads) and /exposure (leads only).
 * Exposure is per market in that market's currency: there is no total across currencies, and bars
 * only compare figures within one row, never across rows.
 */
@Component({
  selector: 'app-dashboard-page',
  imports: [DatePipe, DecimalPipe, MoneyPipe],
  template: `
    <div class="title-row">
      <h1>Dashboard</h1>
      @if (workload(); as w) {
        <span class="muted small">Updated {{ w.generatedAt | date: 'HH:mm:ss' }} · refreshes every 15 s</span>
      }
    </div>

    @if (workload(); as w) {
      <div class="tiles">
        <div class="card tile">
          <span class="label">Unassigned queue</span>
          <span class="value">{{ w.queue?.unassigned }}</span>
          <span class="muted small">{{ w.queue?.oldestAgeDays != null ? 'oldest ' + w.queue?.oldestAgeDays + ' days' : 'empty' }}</span>
        </div>
        <div class="card tile">
          <span class="label">Waiting on claimant</span>
          <span class="value">{{ w.waitingOnClaimant?.count }}</span>
          <span class="muted small">{{ w.waitingOnClaimant?.oldestWaitingDays != null ? 'longest ' + w.waitingOnClaimant?.oldestWaitingDays + ' days' : 'none' }}</span>
        </div>
        <div class="card tile">
          <span class="label">Open claims</span>
          <span class="value">{{ w.totals?.open }}</span>
          <span class="muted small">across all markets</span>
        </div>
        <div class="card tile" [class.alert]="(w.totals?.slaBreaches ?? 0) > 0">
          <span class="label">SLA breaches</span>
          <span class="value">{{ w.totals?.slaBreaches }}</span>
          <span class="muted small">open longer than {{ w.slaDays }} days</span>
        </div>
      </div>

      <h2>Officers</h2>
      <div class="table-wrap"><table>
        <thead>
          <tr>
            <th>Officer</th><th class="num">Open</th><th class="bar-col"></th>
            <th class="num">Under review</th><th class="num">Info requested</th><th class="num">Approved</th>
            <th class="num">SLA breaches</th><th class="num">Decided (7 days)</th><th class="num">Avg days to decision (30 days)</th>
          </tr>
        </thead>
        <tbody>
          @for (o of w.officers; track o.officerId) {
            <tr>
              <td><strong>{{ o.name }}</strong></td>
              <td class="num">{{ o.open }}</td>
              <td class="bar-col"><div class="bar"><span [style.width.%]="share(o.open, maxOpen())"></span></div></td>
              <td class="num">{{ o.byStatus?.['UNDER_REVIEW'] ?? 0 }}</td>
              <td class="num">{{ o.byStatus?.['INFO_REQUESTED'] ?? 0 }}</td>
              <td class="num">{{ o.byStatus?.['APPROVED'] ?? 0 }}</td>
              <td class="num" [class.breach]="(o.slaBreaches ?? 0) > 0">{{ o.slaBreaches }}</td>
              <td class="num">{{ o.decidedLast7Days }}</td>
              <td class="num">{{ o.avgDaysToDecisionLast30 != null ? (o.avgDaysToDecisionLast30 | number: '1.1-1') : '—' }}</td>
            </tr>
          }
        </tbody>
      </table></div>
    } @else {
      <p class="muted">Loading…</p>
    }

    @if (isLead) {
      <h2>Outstanding exposure</h2>
      <p class="muted small note">
        Sum of reserves on open claims, per market in its own currency. Currencies are never added together,
        so there is no grand total; bars show each market's own ageing split.
      </p>
      @if (exposure(); as e) {
        <div class="table-wrap"><table>
          <thead>
            <tr>
              <th>Market</th><th class="num">Open</th><th class="num">Total reserve</th>
              <th class="num">Motor</th><th class="num">Property</th>
              <th class="num">0–7 days</th><th class="num">8–30 days</th><th class="num">31+ days</th><th class="bar-col">Ageing</th>
            </tr>
          </thead>
          <tbody>
            @for (m of e.markets; track m.marketCode) {
              <tr [class.empty]="!m.openClaims">
                <td><strong>{{ m.marketName }}</strong><span class="muted currency">{{ m.currency }}</span></td>
                <td class="num">{{ m.openClaims }}</td>
                <td class="num"><strong>{{ m.totalReserve | money: m.currency }}</strong></td>
                <td class="num">{{ m.byType?.['MOTOR'] | money: m.currency }}</td>
                <td class="num">{{ m.byType?.['PROPERTY'] | money: m.currency }}</td>
                @for (b of buckets; track b) {
                  <td class="num">{{ m.ageing?.[b] | money: m.currency }}</td>
                }
                <td class="bar-col">
                  <div class="bar stacked" [title]="ageingTitle(m)">
                    @for (b of buckets; track b; let i = $index) {
                      <span [class]="'seg seg' + i" [style.width.%]="ageingShare(m, b)"></span>
                    }
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table></div>
        <div class="legend small muted">
          <span><i class="seg0"></i>0–7 days</span><span><i class="seg1"></i>8–30 days</span><span><i class="seg2"></i>31+ days</span>
        </div>
      } @else if (!exposureFailed()) {
        <p class="muted">Loading…</p>
      }
    }
  `,
  styles: `
    .title-row { display: flex; justify-content: space-between; align-items: baseline; gap: var(--space-4); }
    .small { font-size: 0.85rem; }
    .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: var(--space-3); }
    .tile { display: flex; flex-direction: column; gap: var(--space-1); }
    .tile .label { font-size: 0.85rem; color: var(--text-muted); }
    .tile .value { font-size: 2rem; font-weight: 700; line-height: 1.1; }
    .tile.alert { border-color: var(--danger); background: var(--danger-soft); }
    .tile.alert .value { color: var(--danger); }
    h2 { margin-top: var(--space-3); }
    .note { margin: calc(-1 * var(--space-2)) 0 0; }
    .bar-col { width: 120px; min-width: 90px; }
    .bar { height: 10px; background: var(--bg); border-radius: 5px; overflow: hidden; display: flex; }
    .bar > span { display: block; height: 100%; background: var(--accent); }
    .seg0, .legend .seg0 { background: #7fb3ff; }
    .seg1, .legend .seg1 { background: var(--accent); }
    .seg2, .legend .seg2 { background: var(--danger); }
    tr.empty td { color: var(--text-muted); }
    .currency { margin-left: var(--space-2); }
    .legend { display: flex; gap: var(--space-4); }
    .legend i { display: inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: var(--space-1); vertical-align: middle; }
  `,
})
export class DashboardPage {
  private readonly api = inject(ClaimsApi);

  protected readonly isLead = inject(SessionService).role() === 'LEAD';
  protected readonly workload = signal<WorkloadReport | null>(null);
  protected readonly exposure = signal<ExposureReport | null>(null);
  protected readonly exposureFailed = signal(false);
  protected readonly buckets = AGE_BUCKETS;
  protected readonly maxOpen = computed(() => Math.max(0, ...(this.workload()?.officers ?? []).map((o) => o.open ?? 0)));

  constructor() {
    this.refresh();
    pollWhileVisible(() => this.refresh());
  }

  protected share(value: number | undefined, max: number): number {
    return max > 0 ? ((value ?? 0) / max) * 100 : 0;
  }

  /** Share of this market's own total in one ageing bucket (same currency, so the ratio is meaningful). */
  protected ageingShare(m: MarketExposure, bucket: string): number {
    const total = Number(m.totalReserve ?? 0);
    return total > 0 ? (Number(m.ageing?.[bucket] ?? 0) / total) * 100 : 0;
  }

  protected ageingTitle(m: MarketExposure): string {
    return AGE_BUCKETS.map((b) => `${b}: ${Math.round(this.ageingShare(m, b))}%`).join(', ');
  }

  private refresh(): void {
    this.api.workload().then((w) => this.workload.set(w), () => undefined);
    if (this.isLead) {
      // Officers never call /exposure: it is lead-only (the backend would answer 403)
      this.api.exposure().then(
        (e) => this.exposure.set(e),
        () => this.exposureFailed.set(true),
      );
    }
  }
}
