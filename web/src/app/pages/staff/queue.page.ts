import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ClaimSummary } from '../../api/models/claim-summary';
import { ClaimsApi } from '../../core/claims-api.service';
import { ErrorBannerService } from '../../core/error-banner.service';
import { CLAIM_TYPE_LABELS, marketName } from '../../core/markets';
import { pollWhileVisible } from '../../core/polling';
import { SessionService } from '../../core/session.service';
import { MoneyPipe } from '../../shared/money.pipe';

/** Unassigned SUBMITTED claims, oldest first. Officers pick claims up here; leads assign from the workbench. */
@Component({
  selector: 'app-queue-page',
  imports: [MoneyPipe],
  template: `
    <h1>Unassigned queue</h1>
    @if (!loaded()) {
      <p class="muted">Loading…</p>
    } @else if (claims().length === 0) {
      <div class="card muted">The queue is empty. Nothing is waiting to be picked up.</div>
    } @else {
      <table>
        <thead>
          <tr>
            <th>Reference</th><th>Type</th><th>Market</th><th class="num">Claimed</th><th class="num">Age</th>
            @if (isOfficer) { <th></th> }
          </tr>
        </thead>
        <tbody>
          @for (c of claims(); track c.id) {
            <tr class="clickable" tabindex="0" (click)="open(c)" (keydown.enter)="open(c)">
              <td><strong>{{ c.referenceNo }}</strong></td>
              <td>{{ types[c.type ?? ''] }}</td>
              <td>{{ market(c.marketCode) }}</td>
              <td class="num">{{ c.claimedAmount | money: c.currency }}</td>
              <td class="num" [class.breach]="c.slaBreached" [title]="c.slaBreached ? 'Older than the SLA' : ''">
                {{ c.ageDays }} {{ c.ageDays === 1 ? 'day' : 'days' }}
              </td>
              @if (isOfficer) {
                <td class="num">
                  <button type="button" [disabled]="picking() === c.id" (click)="pickUp(c, $event)">
                    {{ picking() === c.id ? 'Picking up…' : 'Pick up' }}
                  </button>
                </td>
              }
            </tr>
          }
        </tbody>
      </table>
    }
  `,
})
export class QueuePage {
  private readonly api = inject(ClaimsApi);
  private readonly router = inject(Router);
  private readonly banners = inject(ErrorBannerService);

  protected readonly isOfficer = inject(SessionService).role() === 'OFFICER';
  protected readonly claims = signal<ClaimSummary[]>([]);
  protected readonly loaded = signal(false);
  protected readonly picking = signal<string | null>(null);
  protected readonly types = CLAIM_TYPE_LABELS;
  protected readonly market = marketName;

  constructor() {
    this.refresh();
    pollWhileVisible(() => this.refresh());
  }

  protected open(c: ClaimSummary): void {
    this.router.navigate(['/work/claims', c.id]);
  }

  protected async pickUp(c: ClaimSummary, event: Event): Promise<void> {
    event.stopPropagation();
    this.picking.set(c.id!);
    try {
      const claim = await this.api.assign(c.id!, c.version!);
      this.banners.success(`You picked up ${claim.referenceNo}`);
      await this.router.navigate(['/work/claims', claim.id]);
    } catch (e) {
      if (!(e instanceof HttpErrorResponse)) {
        throw e;
      }
      this.refresh(); // someone else was faster (409): the banner says so, the list drops the claim
    } finally {
      this.picking.set(null);
    }
  }

  private refresh(): void {
    this.api.search({ unassigned: true }).then(
      (list) => {
        this.claims.set(list);
        this.loaded.set(true);
      },
      () => this.loaded.set(true),
    );
  }
}
