import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from './session.service';
import { Role } from './users';

/** Lets the route through for the given roles; otherwise redirects to the user's home, or /login. */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const session = inject(SessionService);
    return session.hasRole(...roles) || inject(Router).parseUrl(session.home());
  };
}

/** For redirects: wherever the current user belongs. */
export function homeRedirect(): string {
  return inject(SessionService).home();
}
