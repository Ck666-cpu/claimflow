export type Role = 'CLAIMANT' | 'OFFICER' | 'LEAD';

export interface DemoUser {
  id: string;
  name: string;
  role: Role;
}

/** Mirrors the users seeded by V2__seed.sql (see README). Real authentication is out of scope. */
export const DEMO_USERS: readonly DemoUser[] = [
  { id: '00000000-0000-0000-0000-000000000001', name: 'Alice Tan', role: 'CLAIMANT' },
  { id: '00000000-0000-0000-0000-000000000002', name: 'Raj Kumar', role: 'CLAIMANT' },
  { id: '00000000-0000-0000-0000-000000000011', name: 'Ben Lim', role: 'OFFICER' },
  { id: '00000000-0000-0000-0000-000000000012', name: 'Chitra Nair', role: 'OFFICER' },
  { id: '00000000-0000-0000-0000-000000000021', name: 'Daniel Wong', role: 'LEAD' },
];

export const ROLE_LABELS: Record<Role, string> = {
  CLAIMANT: 'Claimant',
  OFFICER: 'Claims officer',
  LEAD: 'Team lead',
};

export function userName(id: string | null | undefined): string {
  if (!id) {
    return 'Unknown';
  }
  return DEMO_USERS.find((u) => u.id === id)?.name ?? 'Unknown user';
}

export function homeFor(user: DemoUser | null): string {
  if (!user) {
    return '/login';
  }
  return user.role === 'CLAIMANT' ? '/claims' : '/work/queue';
}
