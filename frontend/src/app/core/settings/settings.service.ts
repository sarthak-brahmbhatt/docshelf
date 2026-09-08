// Client-side settings (API base URL, bearer token, voice language) as signals, persisted via Capacitor Preferences on native and localStorage on web.
import { Injectable, computed, inject, signal } from '@angular/core';
import { PlatformService } from '../platform/platform.service';

export const SETTINGS_KEYS = {
  apiBaseUrl: 'docshelf.apiBaseUrl',
  apiToken: 'docshelf.apiToken',
  voiceLanguage: 'docshelf.voiceLanguage',
} as const;

export const VOICE_LANGUAGES: readonly { code: string; label: string }[] = [
  { code: 'en-IN', label: 'English (India)' },
  { code: 'hi-IN', label: 'Hindi' },
  { code: 'gu-IN', label: 'Gujarati' },
  { code: 'mr-IN', label: 'Marathi' },
  { code: 'ta-IN', label: 'Tamil' },
  { code: 'te-IN', label: 'Telugu' },
  { code: 'kn-IN', label: 'Kannada' },
  { code: 'bn-IN', label: 'Bengali' },
  { code: 'en-US', label: 'English (US)' },
  { code: 'en-GB', label: 'English (UK)' },
] as const;

const DEFAULT_VOICE_LANGUAGE = 'en-IN';

/** Minimal key-value store contract so the persistence backend can be swapped (and faked in tests). */
export interface KeyValueStore {
  get(key: string): Promise<string | null>;
  set(key: string, value: string): Promise<void>;
  remove(key: string): Promise<void>;
}

class LocalStorageStore implements KeyValueStore {
  async get(key: string): Promise<string | null> {
    try {
      return globalThis.localStorage?.getItem(key) ?? null;
    } catch {
      return null;
    }
  }
  async set(key: string, value: string): Promise<void> {
    try {
      globalThis.localStorage?.setItem(key, value);
    } catch {
      /* storage unavailable (private mode, quota) — settings stay in memory */
    }
  }
  async remove(key: string): Promise<void> {
    try {
      globalThis.localStorage?.removeItem(key);
    } catch {
      /* ignore */
    }
  }
}

class CapacitorPreferencesStore implements KeyValueStore {
  private plugin(): Promise<typeof import('@capacitor/preferences')> {
    return import('@capacitor/preferences');
  }
  async get(key: string): Promise<string | null> {
    try {
      const { Preferences } = await this.plugin();
      const { value } = await Preferences.get({ key });
      return value ?? null;
    } catch {
      return null;
    }
  }
  async set(key: string, value: string): Promise<void> {
    try {
      const { Preferences } = await this.plugin();
      await Preferences.set({ key, value });
    } catch {
      /* ignore */
    }
  }
  async remove(key: string): Promise<void> {
    try {
      const { Preferences } = await this.plugin();
      await Preferences.remove({ key });
    } catch {
      /* ignore */
    }
  }
}

@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly platform = inject(PlatformService);
  private store: KeyValueStore = this.platform.isNative()
    ? new CapacitorPreferencesStore()
    : new LocalStorageStore();

  /** Backend origin, e.g. `http://192.168.1.10:8080`. Empty string = same origin (web via nginx proxy). */
  readonly apiBaseUrl = signal<string>('');
  /** Static bearer token (DOCSHELF_API_TOKEN). Never logged. */
  readonly apiToken = signal<string>('');
  /** BCP-47 language used by the speech recognizer / transcription hint. */
  readonly voiceLanguage = signal<string>(DEFAULT_VOICE_LANGUAGE);
  /** True once persisted values have been read on startup. */
  readonly loaded = signal(false);

  /** On native an empty base URL can never work; Settings shows a warning while this is true. */
  readonly needsBaseUrl = computed(() => this.platform.isNative() && this.apiBaseUrl().trim() === '');
  readonly hasToken = computed(() => this.apiToken().trim() !== '');

  /** Loads persisted values; called once from the app initializer. Never throws. */
  async load(): Promise<void> {
    try {
      const [base, token, lang] = await Promise.all([
        this.store.get(SETTINGS_KEYS.apiBaseUrl),
        this.store.get(SETTINGS_KEYS.apiToken),
        this.store.get(SETTINGS_KEYS.voiceLanguage),
      ]);
      this.apiBaseUrl.set(normalizeBaseUrl(base ?? ''));
      this.apiToken.set(token ?? '');
      this.voiceLanguage.set(lang || DEFAULT_VOICE_LANGUAGE);
    } catch {
      /* keep defaults */
    } finally {
      this.loaded.set(true);
    }
  }

  async setApiBaseUrl(url: string): Promise<void> {
    const normalized = normalizeBaseUrl(url);
    this.apiBaseUrl.set(normalized);
    await this.store.set(SETTINGS_KEYS.apiBaseUrl, normalized);
  }

  async setApiToken(token: string): Promise<void> {
    const trimmed = token.trim();
    this.apiToken.set(trimmed);
    if (trimmed) {
      await this.store.set(SETTINGS_KEYS.apiToken, trimmed);
    } else {
      await this.store.remove(SETTINGS_KEYS.apiToken);
    }
  }

  async setVoiceLanguage(code: string): Promise<void> {
    const value = code || DEFAULT_VOICE_LANGUAGE;
    this.voiceLanguage.set(value);
    await this.store.set(SETTINGS_KEYS.voiceLanguage, value);
  }

  /** Test hook: replace the persistence backend. */
  useStore(store: KeyValueStore): void {
    this.store = store;
  }
}

/** Trims whitespace and trailing slashes; a bare host gets `http://` prepended. */
export function normalizeBaseUrl(raw: string): string {
  let url = (raw ?? '').trim();
  if (!url) return '';
  if (!/^https?:\/\//i.test(url)) {
    url = `http://${url}`;
  }
  return url.replace(/\/+$/, '');
}
