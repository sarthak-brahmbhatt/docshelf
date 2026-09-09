// Local reminder notifications: @capacitor/local-notifications on native (idempotent by id derived from the reminder uuid), Web Notifications API best-effort on web.
import { Injectable, inject, signal } from '@angular/core';
import { Reminder } from '../api/models';
import { PlatformService } from './platform.service';

export interface LocalNotificationSpec {
  /** Stable key (reminder uuid) → deterministic numeric notification id. */
  key: string;
  title: string;
  body: string;
  /** ISO datetime; notifications in the past are skipped. */
  at: string;
  /** Free-form data delivered back when the notification is tapped. */
  extra?: Record<string, unknown>;
}

export interface ScheduleOutcome {
  ok: boolean;
  scheduled: number;
  skipped: number;
  /** Present when ok is false. */
  reason?: 'unsupported' | 'permission-denied' | string;
}

export type LocalNotificationPermission = 'granted' | 'denied' | 'prompt' | 'unsupported';

/** Notification id range: positive 31-bit ints (Android ids are Java ints). */
const ID_MASK = 0x7fffffff;

/** FNV-1a 32-bit hash of the reminder uuid, masked to a positive int — same uuid always maps to the same id. */
export function notificationIdFor(key: string): number {
  let hash = 0x811c9dc5;
  for (let i = 0; i < key.length; i++) {
    hash ^= key.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  const id = hash & ID_MASK;
  return id === 0 ? 1 : id;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly platform = inject(PlatformService);

  /** 'granted' | 'denied' | 'prompt' | 'unsupported' — refreshed by checkPermission()/requestPermission(). */
  readonly permission = signal<LocalNotificationPermission>('prompt');

  constructor() {
    void this.checkPermission();
  }

  async checkPermission(): Promise<LocalNotificationPermission> {
    if (this.platform.isNative()) {
      try {
        const { LocalNotifications } = await import('@capacitor/local-notifications');
        const { display } = await LocalNotifications.checkPermissions();
        this.permission.set(display === 'granted' ? 'granted' : display === 'denied' ? 'denied' : 'prompt');
      } catch {
        this.permission.set('unsupported');
      }
    } else if (typeof Notification === 'undefined') {
      this.permission.set('unsupported');
    } else {
      this.permission.set(Notification.permission === 'default' ? 'prompt' : Notification.permission);
    }
    return this.permission();
  }

  async requestPermission(): Promise<boolean> {
    if (this.platform.isNative()) {
      try {
        const { LocalNotifications } = await import('@capacitor/local-notifications');
        const { display } = await LocalNotifications.requestPermissions();
        this.permission.set(display === 'granted' ? 'granted' : 'denied');
        return display === 'granted';
      } catch {
        this.permission.set('unsupported');
        return false;
      }
    }
    if (typeof Notification === 'undefined') {
      this.permission.set('unsupported');
      return false;
    }
    try {
      const result = await Notification.requestPermission();
      this.permission.set(result === 'default' ? 'prompt' : result);
      return result === 'granted';
    } catch {
      return false;
    }
  }

  /**
   * Mirrors pending reminders into phone notifications. Idempotent: each reminder maps to a fixed id, existing
   * notifications with those ids are cancelled and re-scheduled, and reminders in the past are skipped.
   */
  async scheduleReminders(reminders: Reminder[]): Promise<ScheduleOutcome> {
    const specs: LocalNotificationSpec[] = reminders
      .filter((r) => r.status === 'PENDING' || r.status === 'SNOOZED')
      .map((r) => ({
        key: r.id,
        title: r.title,
        body: r.body || r.documentTitle || '',
        at: r.fireAt ?? `${r.fireDate}T09:00:00`,
        extra: { reminderId: r.id, documentId: r.documentId },
      }));
    return this.schedule(specs);
  }

  /** Schedules arbitrary notifications (used for chat SCHEDULE_NOTIFICATION client actions). */
  async schedule(specs: LocalNotificationSpec[]): Promise<ScheduleOutcome> {
    const now = Date.now();
    const future = specs.filter((s) => new Date(s.at).getTime() > now);
    const skipped = specs.length - future.length;
    if (!future.length) return { ok: true, scheduled: 0, skipped };
    if (!(await this.requestPermission())) {
      return { ok: false, scheduled: 0, skipped, reason: this.permission() === 'unsupported' ? 'unsupported' : 'permission-denied' };
    }
    return this.platform.isNative() ? this.scheduleNative(future, skipped) : this.scheduleWeb(future, skipped);
  }

  /** Cancels one notification by its reminder key. */
  async cancel(key: string): Promise<void> {
    if (!this.platform.isNative()) {
      const timer = this.webTimers.get(key);
      if (timer !== undefined) clearTimeout(timer);
      this.webTimers.delete(key);
      return;
    }
    try {
      const { LocalNotifications } = await import('@capacitor/local-notifications');
      await LocalNotifications.cancel({ notifications: [{ id: notificationIdFor(key) }] });
    } catch {
      /* ignore */
    }
  }

  /** Cancels every pending DocShelf notification. */
  async cancelAll(): Promise<void> {
    if (!this.platform.isNative()) {
      for (const t of this.webTimers.values()) clearTimeout(t);
      this.webTimers.clear();
      return;
    }
    try {
      const { LocalNotifications } = await import('@capacitor/local-notifications');
      const { notifications } = await LocalNotifications.getPending();
      if (notifications.length) {
        await LocalNotifications.cancel({ notifications: notifications.map((n) => ({ id: n.id })) });
      }
    } catch {
      /* ignore */
    }
  }

  /** Number of notifications currently scheduled (native) or timers armed (web). */
  async pendingCount(): Promise<number> {
    if (!this.platform.isNative()) return this.webTimers.size;
    try {
      const { LocalNotifications } = await import('@capacitor/local-notifications');
      return (await LocalNotifications.getPending()).notifications.length;
    } catch {
      return 0;
    }
  }

  private async scheduleNative(specs: LocalNotificationSpec[], skipped: number): Promise<ScheduleOutcome> {
    try {
      const { LocalNotifications } = await import('@capacitor/local-notifications');
      const ids = specs.map((s) => notificationIdFor(s.key));
      // Cancel first so re-syncing replaces instead of duplicating.
      try {
        await LocalNotifications.cancel({ notifications: ids.map((id) => ({ id })) });
      } catch {
        /* nothing pending */
      }
      await LocalNotifications.schedule({
        notifications: specs.map((s, i) => ({
          id: ids[i],
          title: s.title,
          body: s.body,
          schedule: { at: new Date(s.at), allowWhileIdle: true },
          extra: s.extra ?? {},
        })),
      });
      return { ok: true, scheduled: specs.length, skipped };
    } catch (e) {
      return { ok: false, scheduled: 0, skipped, reason: e instanceof Error ? e.message : 'Scheduling failed' };
    }
  }

  // Web: best effort — timers only live while the tab is open.
  private readonly webTimers = new Map<string, ReturnType<typeof setTimeout>>();
  private static readonly MAX_TIMEOUT_MS = 2_147_483_647;

  private scheduleWeb(specs: LocalNotificationSpec[], skipped: number): ScheduleOutcome {
    if (typeof Notification === 'undefined') return { ok: false, scheduled: 0, skipped, reason: 'unsupported' };
    let scheduled = 0;
    for (const spec of specs) {
      const delay = new Date(spec.at).getTime() - Date.now();
      if (delay <= 0 || delay > NotificationService.MAX_TIMEOUT_MS) {
        skipped++;
        continue;
      }
      const existing = this.webTimers.get(spec.key);
      if (existing !== undefined) clearTimeout(existing);
      this.webTimers.set(
        spec.key,
        setTimeout(() => {
          this.webTimers.delete(spec.key);
          try {
            new Notification(spec.title, { body: spec.body, tag: spec.key });
          } catch {
            /* ignore */
          }
        }, delay),
      );
      scheduled++;
    }
    return { ok: true, scheduled, skipped };
  }
}
