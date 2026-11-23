import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Search$Params } from '../../api/fn/claims/search';
import { ClaimSummary } from '../../api/models/claim-summary';
import { ClaimsApi } from '../../core/claims-api.service';
import { pollWhileVisible } from '../../core/polling';
import { SessionService } from '../../core/session.service';
import { userName } from '../../core/users';
import { MoneyPipe } from '../../shared/money.pipe';
import { ClaimStatus, STATUS_LABELS, StatusBadgeComponent } from '../../shared/status-badge.component';

/**
 * Officers: the claims assigned to them (GET /claims?assignee=me, open ones by default).
 * Leads: every claim, open by default, with a status filter (GET /claims?status=…).
 */
@Component({
  selector: 'app-work-list-page',
  imports: [FormsModule, MoneyPipe, StatusBadgeComponent],
  template: `
    <div class="title-row">
      <h1>{{ isLead ? 'Claims' : 'My claims' }}</h1>
      @if (isLead) {
        <label class="filter">
          Status
          <select [ngModel]="status()" (ngModelChange)="setStatus($event)">
            <option value="">All open</option>
            @for (s of statuses; track s) {
              <option [value]="s">{{ labels[s] }}</option>
            }
          </select>
        </label>
      }
    </div>

    @if (!loaded()) {
      <p class="muted">Loading…</p>
    } @else if (claims().length === 0) {
      <div class="card muted">{{ isLead ? 'No claims match this filter.' : 'Nothing assigned to you. Pick up a claim from the queue.' }}</div>
    } @else {
      <table>
        <thead>
          <tr>
            <th>Reference</th><th>Status</th>
            @if (isLead) { <th>Assigned to</th> }
            <th class="num">Claimed</th><th class="num">Reserve</th><th class="num">Age</th><th>SLA</th>
          </tr>
        </thead>
        <tbody>
          @for (c of claims(); track c.id) {
            <tr class="clickable" tabindex="0" (click)="open(c)" (keydown.enter)="open(c)">
              <td><strong>{{ c.referenceNo }}</strong></td>
              <td><app-status-badge [status]="c.status" /></td>
              @if (isLead) { <td>{{ c.assigneeId ? user(c.assigneeId) : '—' }}</td> }
              <td class="num">{{ c.claimedAmount | money: c.currency }}</td>
              <td class="num">{{ c.reserveAmount | money: c.currency }}</td>
              <td class="num">{{ c.ageDays }}d</td>
              <td>@if (c.slaBreached) { <span class="breach">Breached</span> }</td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
  styles: `
    .title-row { display: flex; justify-content: space-between; align-items: center; gap: var(--space-4); }
    .filter { flex-direction: row; align-items: center; gap: var(--space-2); }
    .filter select { width: auto; }
  `,
})
export class WorkListPage {
  private readonly api = inject(ClaimsApi);
  private readonly router = inject(Router);

  protected readonly isLead = inject(SessionService).role() === 'LEAD';
  protected readonly claims = signal<ClaimSummary[]>([]);
  protected readonly loaded = signal(false);
  protected readonly status = signal<ClaimStatus | ''>('');
  protected readonly statuses = Object.keys(STATUS_LABELS) as ClaimStatus[];
  protected readonly labels = STATUS_LABELS;
  protected readonly user = userName;

  constructor() {
    this.refresh();
    pollWhileVisible(() => this.refresh());
  }

  protected setStatus(status: ClaimStatus | ''): void {
    this.status.set(status);
    this.loaded.set(false);
    this.refresh();
  }

  protected open(c: ClaimSummary): void {
    this.router.navigate(['/work/claims', c.id]);
  }

  private refresh(): void {
    const status = this.status();
    // Empty status list = the backend's default (open claims)
    const params: Search$Params = this.isLead ? { status: status ? [status] : undefined } : { assignee: 'me' };
    this.api.search(params).then(
      (list) => {
        if (status === this.status()) {
          this.claims.set(list);
          this.loaded.set(true);
        }
      },
      () => this.loaded.set(true),
    );
  }
}
