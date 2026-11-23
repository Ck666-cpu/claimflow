import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ClaimDetail } from '../../api/models/claim-detail';
import { ClaimsApi } from '../../core/claims-api.service';
import { pollWhileVisible } from '../../core/polling';
import { SessionService } from '../../core/session.service';
import { ClaimOverviewComponent } from '../../shared/claim-overview.component';
import { ClaimTimelineComponent } from '../../shared/claim-timeline.component';
import { ActionsPanelComponent } from './actions-panel.component';

/** Staff view of one claim: details, claimant answers and timeline on the left, actions on the right. */
@Component({
  selector: 'app-workbench-page',
  imports: [RouterLink, ClaimOverviewComponent, ClaimTimelineComponent, ActionsPanelComponent],
  template: `
    <a routerLink="/work/mine" class="back">← {{ session.role() === 'LEAD' ? 'Claims' : 'My claims' }}</a>

    @if (claim(); as c) {
      <div class="layout">
        <div class="main">
          <app-claim-overview [claim]="c" [showReserve]="true" />

          @if (requests().length > 0) {
            <div class="card">
              <h2>Information requests</h2>
              @for (r of requests(); track r.id) {
                <div class="qa">
                  <div class="q">{{ r.question }}</div>
                  @if (r.status === 'ANSWERED') {
                    <div class="a">{{ r.response }}</div>
                  } @else {
                    <div class="muted">Waiting for the claimant…</div>
                  }
                </div>
              }
            </div>
          }

          <div class="card">
            <h2>Timeline</h2>
            <app-claim-timeline [history]="c.history" />
          </div>
        </div>

        <aside>
          <app-actions-panel [claim]="c" [user]="session.user()!" (changed)="claim.set($event)" (stale)="load(id())" />
        </aside>
      </div>
    } @else if (!notFound()) {
      <p class="muted">Loading…</p>
    } @else {
      <div class="card">Claim not found.</div>
    }
  `,
  styles: `
    .back { text-decoration: none; }
    .layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr); gap: var(--space-4); align-items: start; }
    .main { display: flex; flex-direction: column; gap: var(--space-4); min-width: 0; }
    aside { position: sticky; top: var(--space-4); }
    .qa { padding: var(--space-2) 0; border-bottom: 1px solid var(--border); }
    .qa:last-child { border-bottom: none; }
    .q { font-weight: 500; }
    .a { margin-top: var(--space-1); white-space: pre-line; }
    @media (max-width: 760px) { .layout { grid-template-columns: 1fr; } aside { position: static; } }
  `,
})
export class WorkbenchPage {
  private readonly api = inject(ClaimsApi);
  protected readonly session = inject(SessionService);

  readonly id = input.required<string>();
  protected readonly claim = signal<ClaimDetail | null>(null);
  protected readonly notFound = signal(false);
  protected readonly requests = computed(() => this.claim()?.informationRequests ?? []);

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => {
        this.claim.set(null);
        this.load(id);
      });
    });
    pollWhileVisible(() => this.load(this.id()));
  }

  protected load(id: string): void {
    this.api.detail(id).then(
      (claim) => {
        if (claim.id === this.id()) {
          this.claim.set(claim);
        }
      },
      (e: unknown) => {
        if (e instanceof HttpErrorResponse && e.status === 404) {
          this.notFound.set(true);
        }
      },
    );
  }
}
