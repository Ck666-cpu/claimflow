import { Injectable, computed, signal } from '@angular/core';
import { DEMO_USERS, DemoUser, Role, homeFor } from './users';

const STORAGE_KEY = 'claimflow.userId';

/** The selected demo user. Stub authentication: the id is sent as X-User-Id on every request. */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly _user = signal<DemoUser | null>(SessionService.restore());

  readonly user = this._user.asReadonly();
  readonly role = computed<Role | null>(() => this._user()?.role ?? null);
  readonly isClaimant = computed(() => this.role() === 'CLAIMANT');
  readonly isStaff = computed(() => this.role() === 'OFFICER' || this.role() === 'LEAD');
  readonly home = computed(() => homeFor(this._user()));

  login(user: DemoUser): void {
    this._user.set(user);
    try {
      localStorage.setItem(STORAGE_KEY, user.id);
    } catch {
      // storage blocked (private window, policy): the choice just isn't remembered
    }
  }

  hasRole(...roles: Role[]): boolean {
    const role = this.role();
    return role !== null && roles.includes(role);
  }

  private static restore(): DemoUser | null {
    try {
      const id = localStorage.getItem(STORAGE_KEY);
      return DEMO_USERS.find((u) => u.id === id) ?? null;
    } catch {
      return null;
    }
  }
}
