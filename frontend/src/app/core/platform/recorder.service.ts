// Audio recording for voice notes and voice commands: capacitor-voice-recorder on native, MediaRecorder on web; returns a Blob + MIME type.
import { Injectable, computed, inject, signal } from '@angular/core';
import { PlatformService } from './platform.service';

export type RecorderState = 'unsupported' | 'idle' | 'recording' | 'error';

export interface Recording {
  ok: true;
  blob: Blob;
  mimeType: string;
  durationMs: number;
}

export interface RecorderFailure {
  ok: false;
  reason: 'unsupported' | 'permission-denied' | 'not-recording' | string;
}

export type RecordingOutcome = Recording | RecorderFailure;

@Injectable({ providedIn: 'root' })
export class RecorderService {
  private readonly platform = inject(PlatformService);

  readonly state = signal<RecorderState>('idle');
  readonly lastError = signal<string | null>(null);
  readonly supported = computed(() => this.state() !== 'unsupported');
  readonly recording = computed(() => this.state() === 'recording');

  private mediaRecorder: MediaRecorder | null = null;
  private mediaStream: MediaStream | null = null;
  private chunks: Blob[] = [];
  private startedAt = 0;

  constructor() {
    if (!this.platform.isNative() && typeof MediaRecorder === 'undefined') {
      this.state.set('unsupported');
    }
  }

  /** Starts recording; resolves ok=false with a reason instead of throwing. */
  async start(): Promise<{ ok: boolean; reason?: string }> {
    if (this.state() === 'recording') return { ok: true };
    this.lastError.set(null);
    return this.platform.isNative() ? this.startNative() : this.startWeb();
  }

  /** Stops recording and returns the audio. */
  async stop(): Promise<RecordingOutcome> {
    if (this.state() !== 'recording') return { ok: false, reason: 'not-recording' };
    return this.platform.isNative() ? this.stopNative() : this.stopWeb();
  }

  /** Discards the current recording, if any. */
  async cancel(): Promise<void> {
    if (this.state() !== 'recording') return;
    if (this.platform.isNative()) {
      try {
        const { VoiceRecorder } = await import('capacitor-voice-recorder');
        await VoiceRecorder.stopRecording();
      } catch {
        /* ignore */
      }
    } else {
      this.releaseWeb();
    }
    this.state.set('idle');
  }

  private async startNative(): Promise<{ ok: boolean; reason?: string }> {
    try {
      const { VoiceRecorder } = await import('capacitor-voice-recorder');
      const can = await VoiceRecorder.canDeviceVoiceRecord();
      if (!can.value) {
        this.state.set('unsupported');
        return { ok: false, reason: 'unsupported' };
      }
      const has = await VoiceRecorder.hasAudioRecordingPermission();
      if (!has.value) {
        const granted = await VoiceRecorder.requestAudioRecordingPermission();
        if (!granted.value) {
          this.fail('permission-denied');
          return { ok: false, reason: 'permission-denied' };
        }
      }
      const started = await VoiceRecorder.startRecording();
      if (!started.value) {
        this.fail('Recorder did not start');
        return { ok: false, reason: 'Recorder did not start' };
      }
      this.startedAt = Date.now();
      this.state.set('recording');
      return { ok: true };
    } catch (e) {
      const reason = messageOf(e);
      this.fail(reason);
      return { ok: false, reason };
    }
  }

  private async stopNative(): Promise<RecordingOutcome> {
    try {
      const { VoiceRecorder } = await import('capacitor-voice-recorder');
      const data = await VoiceRecorder.stopRecording();
      this.state.set('idle');
      const base64 = data.value.recordDataBase64;
      if (!base64) return { ok: false, reason: 'No audio data returned' };
      const mimeType = data.value.mimeType || 'audio/aac';
      return { ok: true, blob: base64ToBlob(base64, mimeType), mimeType, durationMs: data.value.msDuration };
    } catch (e) {
      const reason = messageOf(e);
      this.fail(reason);
      return { ok: false, reason };
    }
  }

  private async startWeb(): Promise<{ ok: boolean; reason?: string }> {
    if (typeof MediaRecorder === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
      this.state.set('unsupported');
      return { ok: false, reason: 'unsupported' };
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = pickWebMimeType();
      const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
      this.chunks = [];
      recorder.ondataavailable = (ev: BlobEvent) => {
        if (ev.data && ev.data.size > 0) this.chunks.push(ev.data);
      };
      recorder.start(250);
      this.mediaRecorder = recorder;
      this.mediaStream = stream;
      this.startedAt = Date.now();
      this.state.set('recording');
      return { ok: true };
    } catch (e) {
      const reason = isPermissionError(e) ? 'permission-denied' : messageOf(e);
      this.fail(reason);
      return { ok: false, reason };
    }
  }

  private stopWeb(): Promise<RecordingOutcome> {
    return new Promise((resolve) => {
      const recorder = this.mediaRecorder;
      if (!recorder) {
        this.state.set('idle');
        resolve({ ok: false, reason: 'not-recording' });
        return;
      }
      const finish = () => {
        const mimeType = recorder.mimeType || this.chunks[0]?.type || 'audio/webm';
        const blob = new Blob(this.chunks, { type: mimeType });
        const durationMs = Date.now() - this.startedAt;
        this.releaseWeb();
        this.state.set('idle');
        resolve(blob.size > 0 ? { ok: true, blob, mimeType, durationMs } : { ok: false, reason: 'No audio captured' });
      };
      recorder.onstop = finish;
      try {
        if (recorder.state === 'inactive') {
          finish();
        } else {
          recorder.stop();
        }
      } catch (e) {
        this.releaseWeb();
        this.fail(messageOf(e));
        resolve({ ok: false, reason: messageOf(e) });
      }
    });
  }

  private releaseWeb(): void {
    try {
      this.mediaStream?.getTracks().forEach((t) => t.stop());
    } catch {
      /* ignore */
    }
    this.mediaStream = null;
    this.mediaRecorder = null;
    this.chunks = [];
  }

  private fail(reason: string): void {
    this.lastError.set(reason);
    this.state.set('error');
  }
}

function pickWebMimeType(): string | undefined {
  const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus'];
  try {
    return candidates.find((c) => MediaRecorder.isTypeSupported(c));
  } catch {
    return undefined;
  }
}

function base64ToBlob(base64: string, mimeType: string): Blob {
  const clean = base64.includes(',') ? base64.slice(base64.indexOf(',') + 1) : base64;
  const binary = atob(clean);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return new Blob([bytes], { type: mimeType });
}

function isPermissionError(e: unknown): boolean {
  const name = (e as { name?: string } | null)?.name ?? '';
  return name === 'NotAllowedError' || name === 'PermissionDeniedError' || name === 'SecurityError';
}

function messageOf(e: unknown): string {
  return e instanceof Error ? e.message : String(e ?? 'Recording failed');
}
