import { DestroyRef, inject } from '@angular/core';

export const POLL_INTERVAL_MS = 15_000;

/**
 * Calls {@code refresh} every 15 s while the page is visible, and once more when the tab becomes
 * visible again. Stops when the component is destroyed. Call from a constructor or field initializer.
 * Polling instead of server push: see ADR-14.
 */
export function pollWhileVisible(refresh: () => void, intervalMs = POLL_INTERVAL_MS): void {
  const visible = () => typeof document === 'undefined' || document.visibilityState === 'visible';
  const timer = setInterval(() => {
    if (visible()) {
      refresh();
    }
  }, intervalMs);
  const onVisibilityChange = () => {
    if (visible()) {
      refresh();
    }
  };
  document.addEventListener('visibilitychange', onVisibilityChange);
  inject(DestroyRef).onDestroy(() => {
    clearInterval(timer);
    document.removeEventListener('visibilitychange', onVisibilityChange);
  });
}
