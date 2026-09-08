// Answers "are we running inside the Capacitor native shell?" and exposes basic platform info for the Settings page.
import { Injectable } from '@angular/core';
import { Capacitor } from '@capacitor/core';

export interface PlatformInfo {
  /** 'web' | 'android' | 'ios' */
  platform: string;
  isNative: boolean;
  userAgent: string;
  /** Whether the current page is served over https (needed for some Web APIs on web). */
  secureContext: boolean;
}

@Injectable({ providedIn: 'root' })
export class PlatformService {
  /** True when running inside the Android/iOS app (Capacitor bridge present). */
  isNative(): boolean {
    try {
      return Capacitor.isNativePlatform();
    } catch {
      return false;
    }
  }

  /** 'web', 'android' or 'ios'. */
  platform(): string {
    try {
      return Capacitor.getPlatform();
    } catch {
      return 'web';
    }
  }

  isAndroid(): boolean {
    return this.platform() === 'android';
  }

  /** True when a named Capacitor plugin is available on this platform. */
  hasPlugin(name: string): boolean {
    try {
      return Capacitor.isPluginAvailable(name);
    } catch {
      return false;
    }
  }

  info(): PlatformInfo {
    return {
      platform: this.platform(),
      isNative: this.isNative(),
      userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : '',
      secureContext: typeof window !== 'undefined' ? window.isSecureContext === true : false,
    };
  }
}
