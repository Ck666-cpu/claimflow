import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ClaimDetail } from '../../api/models/claim-detail';
import { ClaimsApi } from '../../core/claims-api.service';
import { ErrorBannerService } from '../../core/error-banner.service';
import { pollWhileVisible } from '../../core/polling';
import { nonBlank } from '../../shared/validators';
import { ClaimOverviewComponent } from '../../shared/claim-overview.component';
import { ClaimTimelineComponent } from '../../shared/claim-timeline.component';

@Component({
  selector: 'app-claim-detail-page',
  imports: [ReactiveFormsModule, RouterLink, ClaimOverviewComponent, ClaimTimelineComponent],
  template: `
    <a routerLink="/claims" class="back">← My claims</a>

    @if (claim(); as c) {
      @if (openRequest(); as request) {
        <div class="card question">
          <h2>We need more information</h2>
          <p class="asked">{{ request.question }}</p>
          <label>
            Your reply
            <textarea [formControl]="reply" rows="4" maxlength="4000"></textarea>
            @if (reply.invalid && reply.touched) {
              <span class="field-error">Write a reply before sending.</span>
            }
          </label>
          <div class="actions">
            <button type="button" [disabled]="sending()" (click)="sendReply(request.id!)">
              {{ sending() ? 'Sending…' : 'Send reply' }}
            </button>
          </div>
        </div>
      }

      <app-claim-overview [claim]="c" />

      @if (answered().length > 0) {
        <div class="card">
          <h2>Information you provided</h2>
          @for (r of answered(); track r.id) {
            <div class="qa">
              <div class="muted">{{ r.question }}</div>
              <div>{{ r.response }}</div>
            </div>
          }
        </div>
      }

      <div class="card">
        <h2>Timeline</h2>
        <app-claim-timeline [history]="c.history" />
      </div>

      @if (canWithdraw()) {
        <div class="card withdraw">
          @if (!confirmingWithdraw()) {
            <button type="button" class="secondary" (click)="confirmingWithdraw.set(true)">Withdraw claim</button>
          } @else {
            <span>Withdraw {{ c.referenceNo }}? This cannot be undone.</span>
            <button type="button" class="danger" [disabled]="sending()" (click)="withdraw()">Yes, withdraw</button>
            <button type="button" class="secondary" (click)="confirmingWithdraw.set(false)">Cancel</button>
          }
        </div>
      }
    } @else if (!notFound()) {
      <p class="muted">Loading…</p>
    }
  `,
  styles: `
    .back { text-decoration: none; }
    .question { border-color: var(--warning); background: var(--warning-soft); display: flex; flex-direction: column; gap: var(--space-3); }
    .asked { font-size: 1.05rem; font-weight: 500; margin: 0; white-space: pre-line; }
    .actions { display: flex; justify-content: flex-end; }
    .qa { padding: var(--space-2) 0; border-bottom: 1px solid var(--border); }
    .qa:last-child { border-bottom: none; }
    .withdraw { display: flex; align-items: center; gap: var(--space-3); }
  `,
})
export class ClaimDetailPage {
  private readonly api = inject(ClaimsApi);
  private readonly banners = inject(ErrorBannerService);

  /** Route parameter (withComponentInputBinding). */
  readonly id = input.required<string>();

  protected readonly claim = signal<ClaimDetail | null>(null);
  protected readonly notFound = signal(false);
  protected readonly sending = signal(false);
  protected readonly confirmingWithdraw = signal(false);
  protected readonly reply = new FormControl('', { nonNullable: true, validators: [Validators.required, nonBlank] });

  protected readonly openRequest = computed(() => this.claim()?.informationRequests?.find((r) => r.status === 'OPEN') ?? null);
  protected readonly answered = computed(() => this.claim()?.informationRequests?.filter((r) => r.status === 'ANSWERED') ?? []);
  /** The backend decides what is possible: the button follows allowedNext, the UI does not re-implement the lifecycle. */
  protected readonly canWithdraw = computed(() => this.claim()?.allowedNext?.includes('WITHDRAWN') ?? false);

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

  async sendReply(requestId: string): Promise<void> {
    this.reply.markAsTouched();
    const c = this.claim();
    if (!c || this.reply.invalid) {
      return;
    }
    await this.command(() => this.api.respond(c.id!, requestId, c.version!, this.reply.value.trim()), 'Reply sent');
    this.reply.reset();
  }

  async withdraw(): Promise<void> {
    const c = this.claim();
    if (c) {
      await this.command(() => this.api.withdraw(c.id!, c.version!), `Claim ${c.referenceNo} withdrawn`);
    }
    this.confirmingWithdraw.set(false);
  }

  /** Runs a command with the current version; on 409 the claim changed underneath us, so reload it. */
  private async command(call: () => Promise<ClaimDetail>, successMessage: string): Promise<void> {
    this.sending.set(true);
    try {
      this.claim.set(await call());
      this.banners.success(successMessage);
    } catch (e) {
      if (!(e instanceof HttpErrorResponse)) {
        throw e;
      }
      if (e.status === 409) {
        this.load(this.id()); // the banner shows the server's "changed by someone else" detail
      }
    } finally {
      this.sending.set(false);
    }
  }

  private load(id: string): void {
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
