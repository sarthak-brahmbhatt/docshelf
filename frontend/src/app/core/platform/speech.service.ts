// Speech-to-text for voice commands: @capacitor-community/speech-recognition on native, Web Speech API on web; state exposed as signals.
import { Injectable, NgZone, computed, inject, signal } from '@angular/core';
import { PluginListenerHandle } from '@capacitor/core';
import { SettingsService } from '../settings/settings.service';
import { PlatformService } from './platform.service';

export type SpeechState = 'unsupported' | 'idle' | 'listening' | 'error';

export interface SpeechResult {
  /** Best transcript so far. */
  text: string;
  /** False while partial results are still arriving. */
  final: boolean;
}

export interface SpeechStartOutcome {
  ok: boolean;
  /** Present when ok is false: 'unsupported' | 'permission-denied' | message. */
  reason?: string;
}

/** Minimal typing for the prefixed Web Speech API so we do not depend on lib.dom's experimental declarations. */
interface WebSpeechRecognition {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  onresult: ((ev: { resultIndex: number; results: ArrayLike<ArrayLike<{ transcript: string }> & { isFinal: boolean }> }) => void) | null;
  onerror: ((ev: { error: string }) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
  abort(): void;
}

type WebSpeechCtor = new () => WebSpeechRecognition;

@Injectable({ providedIn: 'root' })
export class SpeechService {
  private readonly platform = inject(PlatformService);
  private readonly settings = inject(SettingsService);
  private readonly zone = inject(NgZone);

  /** 'unsupported' when neither a native recognizer nor the Web Speech API is present. */
  readonly state = signal<SpeechState>('idle');
  /** Latest (partial or final) transcript of the current/last utterance. */
  readonly transcript = signal<string>('');
  readonly lastError = signal<string | null>(null);
  readonly supported = computed(() => this.state() !== 'unsupported');
  readonly listening = computed(() => this.state() === 'listening');

  private resultCallback: ((r: SpeechResult) => void) | null = null;
  private webRecognizer: WebSpeechRecognition | null = null;
  private nativeListeners: PluginListenerHandle[] = [];

  constructor() {
    void this.probeSupport();
  }

  /** Registers a callback for partial/final results; returns an unsubscribe function. */
  onResult(cb: (r: SpeechResult) => void): () => void {
    this.resultCallback = cb;
    return () => {
      if (this.resultCallback === cb) this.resultCallback = null;
    };
  }

  /** Starts listening in the configured voice language (or `language`). Resolves once the recognizer is running. */
  async start(language?: string): Promise<SpeechStartOutcome> {
    const lang = language || this.settings.voiceLanguage();
    if (this.state() === 'listening') return { ok: true };
    this.transcript.set('');
    this.lastError.set(null);
    return this.platform.isNative() ? this.startNative(lang) : this.startWeb(lang);
  }

  /** Stops listening; the final result (if any) is delivered through onResult / transcript. */
  async stop(): Promise<void> {
    if (this.platform.isNative()) {
      try {
        const { SpeechRecognition } = await import('@capacitor-community/speech-recognition');
        await SpeechRecognition.stop();
      } catch {
        /* ignore */
      }
      await this.detachNativeListeners();
    } else {
      try {
        this.webRecognizer?.stop();
      } catch {
        /* ignore */
      }
    }
    if (this.state() === 'listening') this.state.set('idle');
  }

  private async probeSupport(): Promise<void> {
    if (this.platform.isNative()) {
      try {
        const { SpeechRecognition } = await import('@capacitor-community/speech-recognition');
        const { available } = await SpeechRecognition.available();
        this.state.set(available ? 'idle' : 'unsupported');
      } catch {
        this.state.set('unsupported');
      }
      return;
    }
    this.state.set(webSpeechCtor() ? 'idle' : 'unsupported');
  }

  private async startNative(lang: string): Promise<SpeechStartOutcome> {
    try {
      const { SpeechRecognition } = await import('@capacitor-community/speech-recognition');
      const { available } = await SpeechRecognition.available();
      if (!available) {
        this.state.set('unsupported');
        return { ok: false, reason: 'unsupported' };
      }
      const perm = await SpeechRecognition.requestPermissions();
      if (perm.speechRecognition !== 'granted') {
        this.fail('permission-denied');
        return { ok: false, reason: 'permission-denied' };
      }
      await this.detachNativeListeners();
      this.nativeListeners.push(
        await SpeechRecognition.addListener('partialResults', (data) => {
          const text = data.matches?.[0] ?? '';
          this.zone.run(() => this.emit(text, false));
        }),
        await SpeechRecognition.addListener('listeningState', (data) => {
          this.zone.run(() => {
            if (data.status === 'stopped' && this.state() === 'listening') {
              this.state.set('idle');
              this.emit(this.transcript(), true);
            }
          });
        }),
      );
      this.state.set('listening');
      // Resolves when listening ends (Android: after silence or stop()); final matches arrive here.
      void SpeechRecognition.start({ language: lang, partialResults: true, popup: false, maxResults: 3 })
        .then((res) => {
          const text = res?.matches?.[0];
          this.zone.run(() => {
            if (text) this.emit(text, true);
            if (this.state() === 'listening') this.state.set('idle');
          });
        })
        .catch((e: unknown) => this.zone.run(() => this.fail(messageOf(e))));
      return { ok: true };
    } catch (e) {
      this.fail(messageOf(e));
      return { ok: false, reason: messageOf(e) };
    }
  }

  private startWeb(lang: string): SpeechStartOutcome {
    const Ctor = webSpeechCtor();
    if (!Ctor) {
      this.state.set('unsupported');
      return { ok: false, reason: 'unsupported' };
    }
    try {
      const rec = new Ctor();
      rec.lang = lang;
      rec.continuous = false;
      rec.interimResults = true;
      rec.maxAlternatives = 1;
      rec.onresult = (ev) => {
        let text = '';
        let final = false;
        for (let i = 0; i < ev.results.length; i++) {
          const result = ev.results[i];
          text += result[0]?.transcript ?? '';
          if (result.isFinal) final = true;
        }
        this.zone.run(() => this.emit(text.trim(), final));
      };
      rec.onerror = (ev) => {
        this.zone.run(() => {
          if (ev.error === 'aborted' || ev.error === 'no-speech') {
            this.state.set('idle');
          } else {
            this.fail(ev.error === 'not-allowed' ? 'permission-denied' : ev.error);
          }
        });
      };
      rec.onend = () => {
        this.zone.run(() => {
          if (this.state() === 'listening') {
            this.state.set('idle');
            this.emit(this.transcript(), true);
          }
          this.webRecognizer = null;
        });
      };
      this.webRecognizer = rec;
      rec.start();
      this.state.set('listening');
      return { ok: true };
    } catch (e) {
      this.fail(messageOf(e));
      return { ok: false, reason: messageOf(e) };
    }
  }

  private emit(text: string, final: boolean): void {
    if (text) this.transcript.set(text);
    this.resultCallback?.({ text: text || this.transcript(), final });
  }

  private fail(reason: string): void {
    this.lastError.set(reason);
    this.state.set('error');
  }

  private async detachNativeListeners(): Promise<void> {
    const handles = this.nativeListeners;
    this.nativeListeners = [];
    for (const h of handles) {
      try {
        await h.remove();
      } catch {
        /* ignore */
      }
    }
  }
}

function webSpeechCtor(): WebSpeechCtor | null {
  if (typeof window === 'undefined') return null;
  const w = window as unknown as Record<string, unknown>;
  const ctor = (w['SpeechRecognition'] ?? w['webkitSpeechRecognition']) as WebSpeechCtor | undefined;
  return typeof ctor === 'function' ? ctor : null;
}

function messageOf(e: unknown): string {
  return e instanceof Error ? e.message : String(e ?? 'Speech recognition failed');
}
