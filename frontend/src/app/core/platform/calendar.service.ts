// Adds reminders to the phone calendar via @ebarooni/capacitor-calendar (createEventWithPrompt) on native; reports unsupported on web.
import { Injectable, inject } from '@angular/core';
import { PlatformService } from './platform.service';

export interface CalendarEventInput {
  title: string;
  /** ISO datetime. */
  startAt: string;
  /** ISO datetime; defaults to start + 1 hour (or all-day when `allDay`). */
  endAt?: string | null;
  notes?: string | null;
  location?: string | null;
  allDay?: boolean;
  /** Alert offsets in minutes before the event (negative values, e.g. [-60, -1440]). */
  alertsMinutes?: number[];
}

export interface CalendarResult {
  supported: boolean;
  ok: boolean;
  /** Event id when created; null when the user cancelled the prompt. */
  eventId?: string | null;
  reason?: 'unsupported' | 'cancelled' | 'permission-denied' | string;
}

@Injectable({ providedIn: 'root' })
export class CalendarService {
  private readonly platform = inject(PlatformService);

  /** True only inside the native app; the web build shows a disabled button with a tooltip. */
  isSupported(): boolean {
    return this.platform.isNative();
  }

  /** Tooltip text for the disabled web button. */
  unsupportedHint(): string {
    return 'Adding to the calendar is available in the Android app.';
  }

  /** Opens the OS "new event" prompt pre-filled with the reminder. Never throws. */
  async createEventWithPrompt(input: CalendarEventInput): Promise<CalendarResult> {
    if (!this.isSupported()) {
      return { supported: false, ok: false, reason: 'unsupported' };
    }
    try {
      const { CapacitorCalendar } = await import('@ebarooni/capacitor-calendar');
      const start = new Date(input.startAt).getTime();
      if (Number.isNaN(start)) return { supported: true, ok: false, reason: 'Invalid start date' };
      const end = input.endAt ? new Date(input.endAt).getTime() : start + 60 * 60 * 1000;
      const result = await CapacitorCalendar.createEventWithPrompt({
        title: input.title,
        startDate: start,
        endDate: Number.isNaN(end) ? start + 60 * 60 * 1000 : end,
        description: input.notes ?? undefined,
        location: input.location ?? undefined,
        isAllDay: input.allDay ?? false,
        alerts: input.alertsMinutes,
      });
      if (!result.id) return { supported: true, ok: false, eventId: null, reason: 'cancelled' };
      return { supported: true, ok: true, eventId: result.id };
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e ?? 'Calendar failed');
      const lower = message.toLowerCase();
      const reason = lower.includes('permission') || lower.includes('denied') ? 'permission-denied' : message;
      return { supported: true, ok: false, reason };
    }
  }
}
