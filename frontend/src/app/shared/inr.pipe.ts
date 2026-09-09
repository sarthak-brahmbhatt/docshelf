// `inr` pipe: number → Indian-format rupees, e.g. 1234567.5 → ₹12,34,567.50 (lakh/crore grouping); optional compact form.
import { Pipe, PipeTransform } from '@angular/core';

export function formatInr(value: number | null | undefined, fractionDigits = 2, compact = false): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  if (compact) {
    const abs = Math.abs(value);
    const sign = value < 0 ? '-' : '';
    if (abs >= 1e7) return `${sign}₹${(abs / 1e7).toFixed(2)} Cr`;
    if (abs >= 1e5) return `${sign}₹${(abs / 1e5).toFixed(2)} L`;
  }
  try {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: fractionDigits,
    }).format(value);
  } catch {
    return `₹${value.toFixed(fractionDigits)}`;
  }
}

@Pipe({ name: 'inr' })
export class InrPipe implements PipeTransform {
  /** `{{ amount | inr }}`, `{{ amount | inr:0 }}`, `{{ amount | inr:2:true }}` (compact lakh/crore). */
  transform(value: number | null | undefined, fractionDigits = 2, compact = false): string {
    return formatInr(value, fractionDigits, compact);
  }
}
