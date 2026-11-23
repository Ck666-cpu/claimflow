import { Pipe, PipeTransform } from '@angular/core';

const DAY_MS = 86_400_000;

/** "today", "1 day ago", "12 days ago": whole days, rounded down, like the backend's age. */
@Pipe({ name: 'relativeDays' })
export class RelativeDaysPipe implements PipeTransform {
  transform(iso: string | null | undefined, now: Date = new Date()): string {
    if (!iso) {
      return '';
    }
    const days = Math.floor((now.getTime() - new Date(iso).getTime()) / DAY_MS);
    if (days <= 0) {
      return 'today';
    }
    return days === 1 ? '1 day ago' : `${days} days ago`;
  }
}
