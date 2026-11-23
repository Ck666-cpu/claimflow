import { AbstractControl, ValidationErrors, Validators } from '@angular/forms';

/** Today as yyyy-MM-dd in the user's time zone. */
export function todayIso(now: Date = new Date()): string {
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

/** Incident date may not be in the future (mirrors @PastOrPresent on the backend). */
export function notInFuture(control: AbstractControl<string>): ValidationErrors | null {
  return control.value && control.value > todayIso() ? { future: true } : null;
}

/** Positive, at most 12 integer digits and 2 decimals (mirrors @DecimalMin("0.01") @Digits(12, 2)). */
export function moneyAmount(control: AbstractControl<number | null>): ValidationErrors | null {
  const value = control.value;
  if (value === null || value === undefined || (value as unknown) === '') {
    return null; // "required" reports this
  }
  if (!(value >= 0.01)) {
    return { min: true };
  }
  if (!/^\d{1,12}(\.\d{1,2})?$/.test(String(value))) {
    return { format: true };
  }
  return null;
}

/** Like moneyAmount, but zero is allowed (a reserve can be released to 0). */
export function moneyAmountOrZero(control: AbstractControl<number | null>): ValidationErrors | null {
  return control.value === 0 ? null : moneyAmount(control);
}

/** Not empty and not only whitespace. */
export const nonBlank = Validators.pattern(/[\s\S]*\S[\s\S]*/);
