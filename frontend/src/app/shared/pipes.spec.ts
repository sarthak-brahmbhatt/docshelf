// Unit tests for the relativeDate and inr pipes.
import { formatInr } from './inr.pipe';
import { relativeDate } from './relative-date.pipe';

describe('relativeDate', () => {
  const now = new Date(2026, 8, 8, 12, 0, 0); // 8 Sep 2026 12:00 local

  it('handles date-only strings relative to today', () => {
    expect(relativeDate('2026-09-08', now)).toBe('today');
    expect(relativeDate('2026-09-09', now)).toBe('tomorrow');
    expect(relativeDate('2026-09-07', now)).toBe('yesterday');
    expect(relativeDate('2026-09-11', now)).toBe('in 3 days');
    expect(relativeDate('2026-08-29', now)).toBe('10 days ago');
  });

  it('handles timestamps within the day', () => {
    expect(relativeDate(new Date(now.getTime() + 30_000), now)).toBe('just now');
    expect(relativeDate(new Date(now.getTime() + 5 * 60_000), now)).toBe('in 5 min');
    expect(relativeDate(new Date(now.getTime() - 2 * 3_600_000), now)).toBe('2 hours ago');
  });

  it('falls back to a short date beyond 60 days', () => {
    expect(relativeDate('2027-03-14', now)).toMatch(/14 Mar 2027/);
  });

  it('returns empty for missing or invalid input', () => {
    expect(relativeDate(null, now)).toBe('');
    expect(relativeDate('not-a-date', now)).toBe('');
  });
});

describe('formatInr', () => {
  it('uses Indian digit grouping', () => {
    expect(formatInr(1234567.5)).toBe('₹12,34,567.50');
    expect(formatInr(999)).toBe('₹999.00');
    expect(formatInr(24500, 0)).toBe('₹24,500');
  });

  it('formats compact lakh / crore values', () => {
    expect(formatInr(1612340.55, 2, true)).toBe('₹16.12 L');
    expect(formatInr(25_000_000, 2, true)).toBe('₹2.50 Cr');
    expect(formatInr(-150_000, 2, true)).toBe('-₹1.50 L');
    expect(formatInr(5000, 2, true)).toBe('₹5,000.00');
  });

  it('renders a dash for missing values', () => {
    expect(formatInr(null)).toBe('—');
    expect(formatInr(undefined)).toBe('—');
    expect(formatInr(NaN)).toBe('—');
  });
});
