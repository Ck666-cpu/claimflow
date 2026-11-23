import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ClaimsApi } from '../../core/claims-api.service';
import { ErrorBannerService } from '../../core/error-banner.service';
import { MARKETS } from '../../core/markets';
import { moneyAmount, nonBlank, notInFuture, todayIso } from '../../shared/validators';

@Component({
  selector: 'app-new-claim-page',
  imports: [ReactiveFormsModule],
  template: `
    <h1>Report an incident</h1>
    <form class="card form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <fieldset class="row">
        <legend>Type of claim</legend>
        <label class="radio"><input type="radio" formControlName="type" value="MOTOR" /> Motor</label>
        <label class="radio"><input type="radio" formControlName="type" value="PROPERTY" /> Property</label>
        @if (showError('type')) {
          <span class="field-error">Choose a type.</span>
        }
      </fieldset>

      <div class="grid">
        <label>
          Market
          <select formControlName="marketCode">
            <option value="" disabled>Choose a market</option>
            @for (m of markets; track m.code) {
              <option [value]="m.code">{{ m.name }} ({{ m.currency }})</option>
            }
          </select>
          @if (showError('marketCode')) {
            <span class="field-error">Choose the market of your policy.</span>
          }
        </label>

        <label>
          Policy number
          <input formControlName="policyNumber" maxlength="50" autocomplete="off" />
          @if (showError('policyNumber')) {
            <span class="field-error">Enter your policy number (up to 50 characters).</span>
          }
        </label>

        <label>
          Incident date
          <input type="date" formControlName="incidentDate" [max]="today" />
          @if (form.controls.incidentDate.hasError('future') && form.controls.incidentDate.touched) {
            <span class="field-error">The incident date cannot be in the future.</span>
          } @else if (showError('incidentDate')) {
            <span class="field-error">Enter the date of the incident.</span>
          }
        </label>

        <label>
          Claimed amount{{ currency() ? ' (' + currency() + ')' : '' }}
          <input type="number" formControlName="claimedAmount" min="0.01" step="0.01" inputmode="decimal" />
          @if (form.controls.claimedAmount.hasError('min') && form.controls.claimedAmount.touched) {
            <span class="field-error">The amount must be greater than zero.</span>
          } @else if (form.controls.claimedAmount.hasError('format') && form.controls.claimedAmount.touched) {
            <span class="field-error">Use at most 2 decimals.</span>
          } @else if (showError('claimedAmount')) {
            <span class="field-error">Enter the amount you are claiming.</span>
          }
        </label>
      </div>

      <label>
        What happened?
        <textarea formControlName="description" rows="5" maxlength="4000"></textarea>
        <span class="muted small">{{ form.controls.description.value.length }}/4000</span>
        @if (showError('description')) {
          <span class="field-error">Describe what happened.</span>
        }
      </label>

      <div class="actions">
        <button type="submit" [disabled]="sending()">{{ sending() ? 'Submitting…' : 'Submit claim' }}</button>
      </div>
    </form>
  `,
  styles: `
    .form { display: flex; flex-direction: column; gap: var(--space-4); max-width: 760px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: var(--space-4); }
    fieldset { border: none; padding: 0; margin: 0; display: flex; flex-wrap: wrap; gap: var(--space-4); align-items: center; }
    legend { font-weight: 500; margin-bottom: var(--space-2); }
    .radio { flex-direction: row; align-items: center; font-weight: 400; }
    .radio input { width: auto; }
    .small { font-size: 0.8rem; font-weight: 400; }
    .actions { display: flex; justify-content: flex-end; }
  `,
})
export class NewClaimPage {
  private readonly api = inject(ClaimsApi);
  private readonly router = inject(Router);
  private readonly banners = inject(ErrorBannerService);

  protected readonly markets = MARKETS;
  protected readonly today = todayIso();
  protected readonly sending = signal(false);

  /** One key per form session: a double click or a retry after a timeout returns the same claim. */
  private readonly idempotencyKey = crypto.randomUUID();

  readonly form = inject(NonNullableFormBuilder).group({
    type: ['' as '' | 'MOTOR' | 'PROPERTY', Validators.required],
    marketCode: ['', Validators.required],
    policyNumber: ['', [Validators.required, Validators.maxLength(50), nonBlank]],
    incidentDate: ['', [Validators.required, notInFuture]],
    description: ['', [Validators.required, Validators.maxLength(4000), nonBlank]],
    claimedAmount: [null as number | null, [Validators.required, moneyAmount]],
  });

  protected currency(): string {
    return MARKETS.find((m) => m.code === this.form.controls.marketCode.value)?.currency ?? '';
  }

  protected showError(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  async submit(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.sending()) {
      return;
    }
    const v = this.form.getRawValue();
    this.sending.set(true);
    try {
      const claim = await this.api.submit(
        {
          type: v.type as 'MOTOR' | 'PROPERTY',
          marketCode: v.marketCode,
          policyNumber: v.policyNumber.trim(),
          incidentDate: v.incidentDate,
          description: v.description.trim(),
          claimedAmount: Number(Number(v.claimedAmount).toFixed(2)),
        },
        this.idempotencyKey,
      );
      this.banners.success(`Claim ${claim.referenceNo} submitted`, "We'll review it shortly.");
      await this.router.navigate(['/claims', claim.id]);
    } catch (e) {
      if (!(e instanceof HttpErrorResponse)) {
        throw e; // the interceptor has shown HTTP errors; anything else is a bug
      }
    } finally {
      this.sending.set(false);
    }
  }
}
