import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SessionService } from './core/session.service';
import { ROLE_LABELS } from './core/users';
import { ErrorBannerComponent } from './layout/error-banner.component';
import { NotificationsBellComponent } from './layout/notifications-bell.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ErrorBannerComponent, NotificationsBellComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly session = inject(SessionService);
  protected readonly roleLabels = ROLE_LABELS;
}
