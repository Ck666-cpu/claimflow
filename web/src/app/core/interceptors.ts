import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ErrorBannerService, ProblemDetail } from './error-banner.service';
import { SessionService } from './session.service';

/** Sends the selected demo user as X-User-Id (stub authentication, ADR-9). */
export const userIdInterceptor: HttpInterceptorFn = (req, next) => {
  const user = inject(SessionService).user();
  return next(user ? req.clone({ setHeaders: { 'X-User-Id': user.id } }) : req);
};

/**
 * Turns every error response into a ProblemDetail banner, then rethrows so the caller can react
 * (e.g. reload a claim on 409). Errors are never swallowed.
 */
export const problemInterceptor: HttpInterceptorFn = (req, next) => {
  const banners = inject(ErrorBannerService);
  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        banners.showProblem(toProblem(err));
      }
      return throwError(() => err);
    }),
  );
};

export function toProblem(err: HttpErrorResponse): ProblemDetail {
  if (err.status === 0) {
    return { status: 0, title: 'Cannot reach the server', detail: `${err.url ?? 'The API'} is not responding. Is the service running?` };
  }
  const body = err.error;
  if (body && typeof body === 'object' && ('title' in body || 'detail' in body)) {
    return body as ProblemDetail;
  }
  return { status: err.status, title: `Error ${err.status}`, detail: err.statusText || err.message };
}
