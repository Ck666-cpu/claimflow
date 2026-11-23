import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { SessionService } from '../core/session.service';
import { DEMO_USERS, DemoUser, ROLE_LABELS, homeFor } from '../core/users';

@Component({
  selector: 'app-login-page',
  template: `
    <h1>Choose a user</h1>
    <p class="muted">Demo only: pick a seeded user to act as. No password; the choice is sent as <code>X-User-Id</code>.</p>
    <div class="user-grid">
      @for (user of users; track user.id) {
        <button type="button" class="user-card" [class.current]="session.user()?.id === user.id" (click)="choose(user)">
          <span class="user-name">{{ user.name }}</span>
          <span class="muted">{{ roleLabels[user.role] }}</span>
          <span class="hint">{{ hints[user.role] }}</span>
        </button>
      }
    </div>
  `,
  styles: `
    .user-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: var(--space-3); margin-top: var(--space-4); }
    .user-card {
      display: flex; flex-direction: column; align-items: flex-start; gap: var(--space-1);
      padding: var(--space-4); text-align: left; cursor: pointer;
      color: var(--text); background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius);
    }
    .user-card:hover, .user-card:focus-visible { border-color: var(--accent); }
    .user-card.current { border-color: var(--accent); box-shadow: 0 0 0 2px var(--accent-soft); }
    .user-name { font-weight: 600; font-size: 1.05rem; }
    .hint { font-size: 0.85rem; color: var(--text-muted); }
  `,
})
export class LoginPage {
  protected readonly session = inject(SessionService);
  private readonly router = inject(Router);
  protected readonly users = DEMO_USERS;
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly hints = {
    CLAIMANT: 'Report and track claims',
    OFFICER: 'Pick up and assess claims',
    LEAD: 'Assign work, see workload and exposure',
  };

  choose(user: DemoUser): void {
    this.session.login(user);
    this.router.navigateByUrl(homeFor(user));
  }
}
