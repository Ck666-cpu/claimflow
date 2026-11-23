import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ClaimSummary } from '../../api/models/claim-summary';
import { ClaimsApi } from '../../core/claims-api.service';
import { CLAIM_TYPE_LABELS, marketName } from '../../core/markets';
import { pollWhileVisible } from '../../core/polling';
import { MoneyPipe } from '../../shared/money.pipe';
import { RelativeDaysPipe } from '../../shared/relative-days.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

@Component({
  selector: 'app-my-claims-page',
  imports: [RouterLink, MoneyPipe, RelativeDaysPipe, StatusBadgeComponent],
  template: `
    <div class="title-row">
      <h1>My claims</h1>
      @if (claims().length > 0) {
        <a class="button" routerLink="/claims/new">Report an incident</a>
      }
    </div>

    @if (!loaded()) {
      <p class="muted">Loading…</p>
    } @else if (claims().length === 0) {
      <div class="card empty">
        <p>You have no claims yet.</p>
        <a class="button" routerLink="/claims/new">Report an incident</a>
      </div>
    } @else {
      <table>
        <thead>
          <tr>
            <th>Reference</th><th>Type</th><th>Market</th><th class="num">Claimed</th>
            <th>Status</th><th>Submitted</th><th>Last updated</th>
          </tr>
        </thead>
        <tbody>
          @for (c of claims(); track c.id) {
            <tr class="clickable" tabindex="0" (click)="open(c)" (keydown.enter)="open(c)">
              <td><strong>{{ c.referenceNo }}</strong></td>
              <td>{{ types[c.type ?? ''] }}</td>
              <td>{{ market(c.marketCode) }}</td>
              <td class="num">{{ c.claimedAmount | money: c.currency }}</td>
              <td><app-status-badge [status]="c.status" /></td>
              <td>{{ c.submittedAt | relativeDays }}</td>
              <td>{{ c.updatedAt | relativeDays }}</td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
  styles: `
    .title-row { display: flex; justify-content: space-between; align-items: center; }
    .empty { display: flex; flex-direction: column; align-items: flex-start; gap: var(--space-3); }
  `,
})
export class MyClaimsPage {
  private readonly api = inject(ClaimsApi);
  private readonly router = inject(Router);

  protected readonly claims = signal<ClaimSummary[]>([]);
  protected readonly loaded = signal(false);
  protected readonly types = CLAIM_TYPE_LABELS;
  protected readonly market = marketName;

  constructor() {
    this.refresh();
    pollWhileVisible(() => this.refresh());
  }

  protected open(claim: ClaimSummary): void {
    this.router.navigate(['/claims', claim.id]);
  }

  private refresh(): void {
    this.api.mine().then(
      (list) => {
        this.claims.set(list);
        this.loaded.set(true);
      },
      () => this.loaded.set(true), // the interceptor shows the problem
    );
  }
}
