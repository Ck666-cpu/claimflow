import { Component, inject } from '@angular/core';
import { ErrorBannerService } from '../core/error-banner.service';

@Component({
  selector: 'app-error-banner',
  template: `
    @for (banner of banners.banners(); track banner.id) {
      <div class="banner" [class.success]="banner.kind === 'success'" role="alert">
        <div>
          <strong>{{ banner.title }}</strong>
          @if (banner.detail) {
            <span> {{ banner.detail }}</span>
          }
        </div>
        <button type="button" class="link" aria-label="Dismiss" (click)="banners.dismiss(banner.id)">✕</button>
      </div>
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: var(--space-2); }
    .banner {
      display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-3);
      padding: var(--space-3) var(--space-4); border-radius: var(--radius);
      background: var(--danger-soft); border: 1px solid var(--danger); color: var(--text);
    }
    .banner.success { background: var(--success-soft); border-color: var(--success); }
  `,
})
export class ErrorBannerComponent {
  protected readonly banners = inject(ErrorBannerService);
}
