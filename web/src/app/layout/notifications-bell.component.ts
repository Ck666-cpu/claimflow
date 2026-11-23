import { DatePipe } from '@angular/common';
import { Component, ElementRef, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppNotification, NotificationsApi } from '../core/notifications-api.service';
import { pollWhileVisible } from '../core/polling';
import { RelativeDaysPipe } from '../shared/relative-days.pipe';

/**
 * Claimant-only bell: unread count, and a panel listing notifications newest first (unread in bold).
 * Clicking one marks it read and opens its claim. Polls every 15 s.
 */
@Component({
  selector: 'app-notifications-bell',
  imports: [DatePipe, RelativeDaysPipe],
  host: { '(document:click)': 'closeIfOutside($event)', '(document:keydown.escape)': 'open.set(false)' },
  template: `
    <button type="button" class="bell" [attr.aria-expanded]="open()" [attr.aria-label]="unread() + ' unread notifications'"
            title="Notifications" (click)="toggle()">
      🔔
      @if (unread() > 0) {
        <span class="count">{{ unread() }}</span>
      }
    </button>

    @if (open()) {
      <div class="panel" role="dialog" aria-label="Notifications">
        <div class="panel-title">Notifications</div>
        @for (n of notifications(); track n.id) {
          <button type="button" class="item" [class.unread]="!n.read" (click)="openNotification(n)">
            <span class="message">{{ n.message }}</span>
            <span class="when" [title]="n.createdAt | date: 'd MMM y, HH:mm'">{{ n.createdAt | relativeDays }}</span>
          </button>
        } @empty {
          <div class="empty muted">No notifications yet.</div>
        }
      </div>
    }
  `,
  styles: `
    :host { position: relative; }
    .bell { position: relative; font-size: 1.15rem; padding: var(--space-1) var(--space-2); background: none; border: none; color: inherit; }
    .count {
      position: absolute; top: -4px; right: -6px; min-width: 18px; padding: 0 4px;
      font-size: 0.7rem; font-weight: 700; line-height: 18px; text-align: center;
      color: white; background: var(--danger); border-radius: 9px;
    }
    .panel {
      position: absolute; right: 0; top: calc(100% + var(--space-2)); z-index: 10;
      width: 360px; max-height: 420px; overflow-y: auto;
      background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius);
      box-shadow: 0 8px 24px rgb(0 0 0 / 0.12);
    }
    .panel-title { padding: var(--space-3); font-weight: 600; border-bottom: 1px solid var(--border); }
    .item {
      display: flex; gap: var(--space-3); justify-content: space-between; width: 100%;
      padding: var(--space-3); text-align: left; font-weight: 400;
      color: var(--text); background: none; border: none; border-bottom: 1px solid var(--border); border-radius: 0;
    }
    .item:hover { background: var(--accent-soft); }
    .item.unread { font-weight: 600; }
    .when { flex-shrink: 0; font-size: 0.8rem; color: var(--text-muted); font-weight: 400; }
    .empty { padding: var(--space-3); }
  `,
})
export class NotificationsBellComponent {
  private readonly api = inject(NotificationsApi);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);

  protected readonly notifications = signal<AppNotification[]>([]);
  protected readonly unread = computed(() => this.notifications().filter((n) => !n.read).length);
  protected readonly open = signal(false);

  constructor() {
    this.refresh();
    pollWhileVisible(() => this.refresh());
  }

  protected toggle(): void {
    this.open.update((o) => !o);
    if (this.open()) {
      this.refresh();
    }
  }

  protected closeIfOutside(event: Event): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  protected async openNotification(n: AppNotification): Promise<void> {
    this.open.set(false);
    if (!n.read) {
      try {
        const updated = await this.api.markRead(n.id);
        this.notifications.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
      } catch {
        // shown by the interceptor; still open the claim
      }
    }
    await this.router.navigate(['/claims', n.claimId]);
  }

  private refresh(): void {
    this.api.mine().then(
      (list) => this.notifications.set(list),
      () => undefined, // the interceptor already shows the problem in the banner
    );
  }
}
