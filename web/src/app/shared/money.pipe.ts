import { Pipe, PipeTransform } from '@angular/core';

/**
 * Always amount with currency, two decimals: {{ 2800 | money: 'SGD' }} → "SGD 2,800.00".
 * Accepts numbers (claims API) and decimal strings (metrics API). Empty for null/undefined.
 */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(amount: number | string | null | undefined, currency: string | null | undefined): string {
    if (amount === null || amount === undefined || amount === '') {
      return '';
    }
    const value = typeof amount === 'string' ? Number(amount) : amount;
    if (Number.isNaN(value)) {
      return `${currency ?? ''} ${amount}`.trim();
    }
    const formatted = value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return `${currency ?? ''} ${formatted}`.trim();
  }
}
