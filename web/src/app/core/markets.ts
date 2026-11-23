/** The six markets seeded by V2__seed.sql; each has one currency. */
export const MARKETS = [
  { code: 'SG', name: 'Singapore', currency: 'SGD' },
  { code: 'MY', name: 'Malaysia', currency: 'MYR' },
  { code: 'HK', name: 'Hong Kong', currency: 'HKD' },
  { code: 'AU', name: 'Australia', currency: 'AUD' },
  { code: 'TH', name: 'Thailand', currency: 'THB' },
  { code: 'ID', name: 'Indonesia', currency: 'IDR' },
] as const;

export function marketName(code: string | null | undefined): string {
  return MARKETS.find((m) => m.code === code)?.name ?? code ?? '';
}

export const CLAIM_TYPE_LABELS: Record<string, string> = { MOTOR: 'Motor', PROPERTY: 'Property' };
