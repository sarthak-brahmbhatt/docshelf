// Unit tests for SettingsService: persistence round-trip, URL normalisation, native warning flag.
import { TestBed } from '@angular/core/testing';
import { PlatformService } from '../platform/platform.service';
import { KeyValueStore, SETTINGS_KEYS, SettingsService, normalizeBaseUrl } from './settings.service';

class MemoryStore implements KeyValueStore {
  readonly map = new Map<string, string>();
  async get(key: string): Promise<string | null> {
    return this.map.get(key) ?? null;
  }
  async set(key: string, value: string): Promise<void> {
    this.map.set(key, value);
  }
  async remove(key: string): Promise<void> {
    this.map.delete(key);
  }
}

describe('normalizeBaseUrl', () => {
  it('returns empty string for blank input', () => {
    expect(normalizeBaseUrl('')).toBe('');
    expect(normalizeBaseUrl('   ')).toBe('');
  });

  it('prepends http:// when no scheme is given', () => {
    expect(normalizeBaseUrl('192.168.1.10:8080')).toBe('http://192.168.1.10:8080');
  });

  it('strips trailing slashes and whitespace', () => {
    expect(normalizeBaseUrl('  https://docs.local/  ')).toBe('https://docs.local');
    expect(normalizeBaseUrl('http://host:8080///')).toBe('http://host:8080');
  });
});

describe('SettingsService (web)', () => {
  let service: SettingsService;
  let store: MemoryStore;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SettingsService);
    store = new MemoryStore();
    service.useStore(store);
  });

  it('starts with same-origin defaults and no token', () => {
    expect(service.apiBaseUrl()).toBe('');
    expect(service.apiToken()).toBe('');
    expect(service.voiceLanguage()).toBe('en-IN');
    expect(service.needsBaseUrl()).toBe(false);
    expect(service.hasToken()).toBe(false);
  });

  it('persists and normalises the base URL', async () => {
    await service.setApiBaseUrl('192.168.1.10:8080/');
    expect(service.apiBaseUrl()).toBe('http://192.168.1.10:8080');
    expect(store.map.get(SETTINGS_KEYS.apiBaseUrl)).toBe('http://192.168.1.10:8080');
  });

  it('persists the token and removes it when cleared', async () => {
    await service.setApiToken('  secret-token  ');
    expect(service.apiToken()).toBe('secret-token');
    expect(service.hasToken()).toBe(true);
    expect(store.map.get(SETTINGS_KEYS.apiToken)).toBe('secret-token');

    await service.setApiToken('');
    expect(service.apiToken()).toBe('');
    expect(store.map.has(SETTINGS_KEYS.apiToken)).toBe(false);
  });

  it('loads persisted values on load()', async () => {
    store.map.set(SETTINGS_KEYS.apiBaseUrl, 'http://box:8080/');
    store.map.set(SETTINGS_KEYS.apiToken, 'tok');
    store.map.set(SETTINGS_KEYS.voiceLanguage, 'hi-IN');
    await service.load();
    expect(service.loaded()).toBe(true);
    expect(service.apiBaseUrl()).toBe('http://box:8080');
    expect(service.apiToken()).toBe('tok');
    expect(service.voiceLanguage()).toBe('hi-IN');
  });

  it('falls back to the default voice language when nothing is stored', async () => {
    await service.setVoiceLanguage('');
    expect(service.voiceLanguage()).toBe('en-IN');
  });
});

describe('SettingsService (native)', () => {
  it('flags a missing base URL on native platforms', async () => {
    TestBed.configureTestingModule({
      providers: [{ provide: PlatformService, useValue: { isNative: () => true, platform: () => 'android' } }],
    });
    const service = TestBed.inject(SettingsService);
    service.useStore(new MemoryStore());
    expect(service.needsBaseUrl()).toBe(true);
    await service.setApiBaseUrl('http://10.0.0.5:8080');
    expect(service.needsBaseUrl()).toBe(false);
  });
});
