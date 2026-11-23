import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Shape of notification-service's NotificationView (not part of the generated claims contract). */
export interface AppNotification {
  id: string;
  claimId: string;
  eventType: string;
  message: string;
  createdAt: string;
  read: boolean;
}

/** notification-service on :8081, reached through the dev proxy (/api/v1/notifications). */
@Injectable({ providedIn: 'root' })
export class NotificationsApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/notifications';

  mine(): Promise<AppNotification[]> {
    return firstValueFrom(this.http.get<AppNotification[]>(`${this.base}/mine`));
  }

  markRead(id: string): Promise<AppNotification> {
    return firstValueFrom(this.http.post<AppNotification>(`${this.base}/${id}/read`, null));
  }
}
