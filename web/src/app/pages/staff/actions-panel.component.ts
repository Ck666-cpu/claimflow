import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ClaimDetail } from '../../api/models/claim-detail';
import { ClaimsApi } from '../../core/claims-api.service';
import { ErrorBannerService } from '../../core/error-banner.service';
import { DEMO_USERS, DemoUser, userName } from '../../core/users';
import { MoneyPipe } from '../../shared/money.pipe';
import { moneyAmount, moneyAmountOrZero, nonBlank } from '../../shared/validators';

type Action = 'info' | 'reserve' | 'approve' | 'reject' | 'settle' | 'reassign' | 'pickup';

const OPEN_STATUSES = ['SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED', 'APPROVED'];

/**
 * Staff actions for one claim. Which actions appear comes from the API: the claim's allowedNext and
 * the user's role. The UI never re-implements the lifecycle (ADR-13); the backend stays the authority
 * and answers 409/403 if something changed. Every command sends the claim's current version.
 */
@Component({
  selector: 'app-actions-panel',
  imports: [ReactiveFormsModule, MoneyPipe],
  template: `
    @let c = claim();
    <div class="card panel">
      <h2>Actions</h2>

      @if (!canAct()) {
        <p class="note">
          @if (c.assigneeId) {
            Read only: this claim is assigned to {{ name(c.assigneeId) }}.
          } @else {
            Read only: this claim is not assigned yet.
          }
        </p>
        @if (canPickUp()) {
          <button type="button" [disabled]="busy()" (click)="pickUp()">{{ busy() === 'pickup' ? 'Picking up…' : 'Pick up' }}</button>
        }
      } @else {
        @if (allows('INFO_REQUESTED')) {
          <section>
            <h3>Request information</h3>
            <textarea [formControl]="question" rows="3" maxlength="4000" placeholder="What do you need from the claimant?"></textarea>
            <button type="button" [disabled]="busy() || question.invalid" (click)="requestInfo()">
              {{ busy() === 'info' ? 'Sending…' : 'Send request' }}
            </button>
          </section>
        }

        @if (c.status === 'UNDER_REVIEW' || c.status === 'INFO_REQUESTED') {
          <section>
            <h3>Reserve</h3>
            <p class="muted">Current: <strong>{{ c.reserveAmount | money: c.currency }}</strong></p>
            <div class="inline">
              <input type="number" min="0" step="0.01" [formControl]="reserve" [attr.aria-label]="'Reserve in ' + c.currency" />
              <button type="button" [disabled]="busy() || reserve.invalid" (click)="setReserve()">
                {{ busy() === 'reserve' ? 'Saving…' : 'Set reserve' }}
              </button>
            </div>
          </section>
        }

        @if (allows('APPROVED') || allows('REJECTED')) {
          <section>
            <h3>Decide</h3>
            @if (allows('APPROVED')) {
              <label>
                Approved amount ({{ c.currency }})
                <div class="inline">
                  <input type="number" min="0.01" step="0.01" [formControl]="approvedAmount" />
                  <button type="button" [disabled]="busy() || approvedAmount.invalid" (click)="approve()">
                    {{ busy() === 'approve' ? 'Approving…' : 'Approve' }}
                  </button>
                </div>
              </label>
            }
            @if (allows('REJECTED')) {
              <label>
                Reason for rejection
                <textarea [formControl]="reason" rows="2" maxlength="4000"></textarea>
              </label>
              <button type="button" class="danger" [disabled]="busy() || reason.invalid" (click)="reject()">
                {{ busy() === 'reject' ? 'Rejecting…' : 'Reject' }}
              </button>
            }
          </section>
        }

        @if (allows('SETTLED')) {
          <section class="settle">
            <h3>Settle</h3>
            @if (!confirmSettle()) {
              <button type="button" (click)="confirmSettle.set(true)">Mark as settled</button>
            } @else {
              <p>Confirm payment of {{ c.approvedAmount | money: c.currency }} was made?</p>
              <div class="inline">
                <button type="button" [disabled]="busy()" (click)="settle()">{{ busy() === 'settle' ? 'Settling…' : 'Yes, settled' }}</button>
                <button type="button" class="secondary" (click)="confirmSettle.set(false)">Cancel</button>
              </div>
            }
          </section>
        }

        @if (canReassign()) {
          <section>
            <h3>{{ c.assigneeId ? 'Reassign' : 'Assign' }}</h3>
            <div class="inline">
              <select [formControl]="assignee" aria-label="Officer">
                <option value="" disabled>Choose an officer</option>
                @for (o of officers(); track o.id) {
                  <option [value]="o.id">{{ o.name }}</option>
                }
              </select>
              <button type="button" [disabled]="busy() || !assignee.value" (click)="reassign()">
                {{ busy() === 'reassign' ? 'Saving…' : c.assigneeId ? 'Reassign' : 'Assign' }}
              </button>
            </div>
          </section>
        }

        @if (nothingToDo()) {
          <p class="muted">No actions: this claim is {{ c.status === 'SETTLED' ? 'settled' : 'closed' }}.</p>
        }
      }
    </div>
  `,
  styles: `
    .panel { display: flex; flex-direction: column; gap: var(--space-4); }
    .panel h2 { margin: 0; }
    section { display: flex; flex-direction: column; gap: var(--space-2); padding-top: var(--space-3); border-top: 1px solid var(--border); }
    h3 { font-size: 0.95rem; margin: 0; }
    .inline { display: flex; gap: var(--space-2); align-items: center; }
    .inline input, .inline select { flex: 1; min-width: 0; }
    .note { margin: 0; padding: var(--space-3); border-radius: var(--radius); background: var(--warning-soft); }
    p { margin: 0; }
  `,
})
export class ActionsPanelComponent {
  private readonly api = inject(ClaimsApi);
  private readonly banners = inject(ErrorBannerService);

  readonly claim = input.required<ClaimDetail>();
  readonly user = input.required<DemoUser>();
  /** The command succeeded: the returned claim (with its new version). */
  readonly changed = output<ClaimDetail>();
  /** The claim changed underneath us (409): the parent reloads it. */
  readonly stale = output<void>();

  protected readonly busy = signal<Action | null>(null);
  protected readonly confirmSettle = signal(false);
  protected readonly name = userName;

  protected readonly question = new FormControl('', { nonNullable: true, validators: [Validators.required, nonBlank] });
  protected readonly reserve = new FormControl<number | null>(null, [Validators.required, Validators.min(0), moneyAmountOrZero]);
  protected readonly approvedAmount = new FormControl<number | null>(null, [Validators.required, moneyAmount]);
  protected readonly reason = new FormControl('', { nonNullable: true, validators: [Validators.required, nonBlank] });
  protected readonly assignee = new FormControl('', { nonNullable: true });

  /** Leads act on any claim; officers only on claims assigned to them (the backend enforces the same). */
  protected readonly canAct = computed(() => {
    const u = this.user();
    return u.role === 'LEAD' || (u.role === 'OFFICER' && this.claim().assigneeId === u.id);
  });
  protected readonly canPickUp = computed(
    () => this.user().role === 'OFFICER' && !this.claim().assigneeId && this.claim().status === 'SUBMITTED',
  );
  protected readonly canReassign = computed(() => this.user().role === 'LEAD' && OPEN_STATUSES.includes(this.claim().status ?? ''));
  protected readonly officers = computed(() => DEMO_USERS.filter((u) => u.role === 'OFFICER' && u.id !== this.claim().assigneeId));
  protected readonly nothingToDo = computed(() => (this.claim().allowedNext ?? []).length === 0 && !this.canReassign());

  constructor() {
    // Prefill from the latest claim, without overwriting what the user is typing during a poll refresh
    effect(() => {
      const c = this.claim();
      if (this.reserve.pristine) {
        this.reserve.setValue(c.reserveAmount ?? null);
      }
      if (this.approvedAmount.pristine) {
        this.approvedAmount.setValue(c.reserveAmount ?? null);
      }
    });
  }

  protected allows(status: string): boolean {
    return (this.claim().allowedNext ?? []).includes(status as never);
  }

  protected requestInfo(): Promise<void> {
    return this.run('info', 'Information requested', (c) => this.api.requestInformation(c.id!, c.version!, this.question.value.trim()), () =>
      this.question.reset(),
    );
  }

  protected setReserve(): Promise<void> {
    const amount = round2(this.reserve.value);
    return this.run('reserve', 'Reserve updated', (c) => this.api.changeReserve(c.id!, c.version!, amount), () => this.reserve.markAsPristine());
  }

  protected approve(): Promise<void> {
    const amount = round2(this.approvedAmount.value);
    return this.run('approve', 'Claim approved', (c) => this.api.decide(c.id!, { outcome: 'APPROVE', approvedAmount: amount, version: c.version! }));
  }

  protected reject(): Promise<void> {
    return this.run('reject', 'Claim rejected', (c) =>
      this.api.decide(c.id!, { outcome: 'REJECT', reason: this.reason.value.trim(), version: c.version! }),
    );
  }

  protected settle(): Promise<void> {
    return this.run('settle', 'Claim settled', (c) => this.api.settle(c.id!, c.version!), () => this.confirmSettle.set(false));
  }

  protected reassign(): Promise<void> {
    const officer = this.assignee.value;
    return this.run('reassign', `Assigned to ${userName(officer)}`, (c) => this.api.assign(c.id!, c.version!, officer), () =>
      this.assignee.reset(),
    );
  }

  protected pickUp(): Promise<void> {
    return this.run('pickup', 'You picked up this claim', (c) => this.api.assign(c.id!, c.version!));
  }

  private async run(action: Action, success: string, call: (c: ClaimDetail) => Promise<ClaimDetail>, after?: () => void): Promise<void> {
    this.busy.set(action);
    try {
      const updated = await call(this.claim());
      this.banners.success(success);
      after?.();
      this.changed.emit(updated);
    } catch (e) {
      if (!(e instanceof HttpErrorResponse)) {
        throw e;
      }
      if (e.status === 409) {
        this.stale.emit(); // the banner shows "changed by someone else"; the parent reloads
      }
    } finally {
      this.busy.set(null);
    }
  }
}

function round2(value: number | null): number {
  return Number(Number(value ?? 0).toFixed(2));
}
