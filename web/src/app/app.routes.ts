import { Routes } from '@angular/router';
import { homeRedirect, roleGuard } from './core/role.guard';

/**
 * Every page is lazy-loaded and guarded by role. Unknown or forbidden URLs go to the user's home
 * (/claims for claimants, /work/queue for staff), or /login when nobody is selected.
 */
export const routes: Routes = [
  { path: 'login', title: 'Choose a user', loadComponent: () => import('./pages/login.page').then((m) => m.LoginPage) },
  {
    path: 'claims',
    canActivate: [roleGuard('CLAIMANT')],
    children: [
      { path: '', title: 'My claims', loadComponent: () => import('./pages/claimant/my-claims.page').then((m) => m.MyClaimsPage) },
      { path: 'new', title: 'Report an incident', loadComponent: () => import('./pages/claimant/new-claim.page').then((m) => m.NewClaimPage) },
      { path: ':id', title: 'Claim', loadComponent: () => import('./pages/claimant/claim-detail.page').then((m) => m.ClaimDetailPage) },
    ],
  },
  {
    path: 'work',
    canActivate: [roleGuard('OFFICER', 'LEAD')],
    children: [
      { path: 'queue', title: 'Queue', loadComponent: () => import('./pages/staff/queue.page').then((m) => m.QueuePage) },
      { path: 'mine', title: 'Claims', loadComponent: () => import('./pages/staff/work-list.page').then((m) => m.WorkListPage) },
      {
        path: 'claims/:id',
        title: 'Claim workbench',
        loadComponent: () => import('./pages/staff/workbench.page').then((m) => m.WorkbenchPage),
      },
      {
        path: 'dashboard',
        title: 'Dashboard',
        canActivate: [roleGuard('OFFICER', 'LEAD')], // officers see workload only; exposure is lead-only
        loadComponent: () => import('./pages/staff/dashboard.page').then((m) => m.DashboardPage),
      },
      { path: '', pathMatch: 'full', redirectTo: 'queue' },
    ],
  },
  { path: '**', redirectTo: () => homeRedirect() },
];
