// `relativeDate` pipe: ISO date/datetime → "today", "in 3 days", "2 hours ago", "yesterday" (falls back to a short date beyond ~60 days).
import { Pipe, PipeTransform } from '@angular/core';

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

export function relativeDate(value: string | Date | null | undefined, now: Date = new Date()): string {
  if (!value) return '';
  const isDateOnly = typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value);
  const target = typeof value === 'string' ? new Date(isDateOnly ? `${value}T00:00:00` : value) : value;
  if (Number.isNaN(target.getTime())) return '';

  if (isDateOnly) {
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const days = Math.round((target.getTime() - startOfToday.getTime()) / DAY);
    return describeDays(days, target);
  }

  const diff = target.getTime() - now.getTime();
  const abs = Math.abs(diff);
  const future = diff > 0;
  if (abs < MINUTE) return 'just now';
  if (abs < HOUR) {
    const m = Math.round(abs / MINUTE);
    return future ? `in ${m} min` : `${m} min ago`;
  }
  if (abs < DAY) {
    const h = Math.round(abs / HOUR);
    return future ? `in ${h} hour${h === 1 ? '' : 's'}` : `${h} hour${h === 1 ? '' : 's'} ago`;
  }
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const startOfTarget = new Date(target.getFullYear(), target.getMonth(), target.getDate());
  const days = Math.round((startOfTarget.getTime() - startOfToday.getTime()) / DAY);
  return describeDays(days, target);
}

function describeDays(days: number, target: Date): string {
  if (days === 0) return 'today';
  if (days === 1) return 'tomorrow';
  if (days === -1) return 'yesterday';
  if (Math.abs(days) <= 60) return days > 0 ? `in ${days} days` : `${-days} days ago`;
  return target.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

@Pipe({ name: 'relativeDate' })
export class RelativeDatePipe implements PipeTransform {
  transform(value: string | Date | null | undefined): string {
    return relativeDate(value);
  }
}
