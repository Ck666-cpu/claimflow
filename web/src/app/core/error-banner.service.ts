import { Injectable, signal } from '@angular/core';

/** RFC 9457 problem details as returned by both services. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: { field: string; message: string }[];
}

export interface Banner {
  id: number;
  kind: 'error' | 'success';
  title: string;
  detail?: string;
}

/** Dismissible messages at the top of the page. Identical errors are shown once, not stacked. */
@Injectable({ providedIn: 'root' })
export class ErrorBannerService {
  private nextId = 1;
  private readonly _banners = signal<Banner[]>([]);
  readonly banners = this._banners.asReadonly();

  showProblem(problem: ProblemDetail): void {
    const fieldErrors = problem.errors?.map((e) => `${e.field}: ${e.message}`).join('; ');
    this.push('error', problem.title || `Error ${problem.status ?? ''}`.trim(), fieldErrors || problem.detail);
  }

  success(title: string, detail?: string): void {
    const id = this.push('success', title, detail);
    setTimeout(() => this.dismiss(id), 5000);
  }

  dismiss(id: number): void {
    this._banners.update((list) => list.filter((b) => b.id !== id));
  }

  private push(kind: Banner['kind'], title: string, detail?: string): number {
    const existing = this._banners().find((b) => b.kind === kind && b.title === title && b.detail === detail);
    if (existing) {
      return existing.id;
    }
    const id = this.nextId++;
    this._banners.update((list) => [...list, { id, kind, title, detail }]);
    return id;
  }
}
