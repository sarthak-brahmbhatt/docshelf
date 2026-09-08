// Captures a photo or picks a file: Capacitor Camera on native, a hidden <input type=file> on web; always resolves to a result object, never throws.
import { Injectable, inject } from '@angular/core';
import { PlatformService } from './platform.service';

export interface CaptureResult {
  ok: true;
  file: File;
  /** 'CAMERA' when the image came from the camera, 'UPLOAD' when picked from files/gallery. */
  source: 'CAMERA' | 'UPLOAD';
}

export interface CaptureFailure {
  ok: false;
  /** 'cancelled' when the user dismissed the picker; 'unsupported' when no capture path exists; otherwise a message. */
  reason: 'cancelled' | 'unsupported' | 'permission-denied' | string;
}

export type CaptureOutcome = CaptureResult | CaptureFailure;

export interface PickFileOptions {
  /** Accept attribute, e.g. 'image/*' or 'application/pdf,image/*'. Default: any file. */
  accept?: string;
  multiple?: boolean;
}

export interface PickFilesResult {
  ok: true;
  files: File[];
}

export type PickFilesOutcome = PickFilesResult | CaptureFailure;

@Injectable({ providedIn: 'root' })
export class CameraService {
  private readonly platform = inject(PlatformService);

  /** Takes a photo with the rear camera. On web this opens the file chooser with `capture="environment"`. */
  async takePhoto(): Promise<CaptureOutcome> {
    if (this.platform.isNative()) {
      return this.takePhotoNative();
    }
    const picked = await openFileInput({ accept: 'image/*', capture: 'environment' });
    if (!picked.ok) return picked;
    return { ok: true, file: picked.files[0], source: 'CAMERA' };
  }

  /** Picks one image from the gallery (native) or file system (web). */
  async pickImage(): Promise<CaptureOutcome> {
    if (this.platform.isNative()) {
      return this.pickImageNative();
    }
    const picked = await openFileInput({ accept: 'image/*' });
    if (!picked.ok) return picked;
    return { ok: true, file: picked.files[0], source: 'UPLOAD' };
  }

  /** Picks arbitrary files (PDF, images, anything). Uses the browser/WebView file chooser on both platforms. */
  async pickFiles(opts: PickFileOptions = {}): Promise<PickFilesOutcome> {
    return openFileInput({ accept: opts.accept, multiple: opts.multiple });
  }

  private async takePhotoNative(): Promise<CaptureOutcome> {
    try {
      const { Camera } = await import('@capacitor/camera');
      const perm = await Camera.requestPermissions({ permissions: ['camera'] });
      if (perm.camera === 'denied') return { ok: false, reason: 'permission-denied' };
      const result = await Camera.takePhoto({ quality: 85, correctOrientation: true, saveToGallery: false });
      return this.mediaToFile(result.webPath ?? result.uri, 'CAMERA');
    } catch (e) {
      return { ok: false, reason: classifyError(e) };
    }
  }

  private async pickImageNative(): Promise<CaptureOutcome> {
    try {
      const { Camera } = await import('@capacitor/camera');
      const perm = await Camera.requestPermissions({ permissions: ['photos'] });
      if (perm.photos === 'denied') return { ok: false, reason: 'permission-denied' };
      const result = await Camera.chooseFromGallery({ limit: 1, quality: 85 });
      const first = result.results?.[0];
      if (!first) return { ok: false, reason: 'cancelled' };
      return this.mediaToFile(first.webPath ?? first.uri, 'UPLOAD');
    } catch (e) {
      return { ok: false, reason: classifyError(e) };
    }
  }

  private async mediaToFile(path: string | undefined, source: 'CAMERA' | 'UPLOAD'): Promise<CaptureOutcome> {
    if (!path) return { ok: false, reason: 'cancelled' };
    try {
      const { Capacitor } = await import('@capacitor/core');
      const url = path.startsWith('http') || path.startsWith('blob:') ? path : Capacitor.convertFileSrc(path);
      const blob = await (await fetch(url)).blob();
      const type = blob.type || 'image/jpeg';
      const ext = type.includes('png') ? 'png' : type.includes('webp') ? 'webp' : 'jpg';
      return { ok: true, file: new File([blob], `photo-${Date.now()}.${ext}`, { type }), source };
    } catch (e) {
      return { ok: false, reason: classifyError(e) };
    }
  }
}

function classifyError(e: unknown): string {
  const message = e instanceof Error ? e.message : String(e ?? '');
  const lower = message.toLowerCase();
  if (lower.includes('cancel')) return 'cancelled';
  if (lower.includes('permission') || lower.includes('denied')) return 'permission-denied';
  if (lower.includes('not implemented') || lower.includes('not available')) return 'unsupported';
  return message || 'Capture failed';
}

/** Opens a transient <input type=file>; resolves with the picked files or a cancelled/unsupported failure. */
function openFileInput(opts: { accept?: string; multiple?: boolean; capture?: string }): Promise<PickFilesOutcome> {
  if (typeof document === 'undefined') {
    return Promise.resolve({ ok: false, reason: 'unsupported' });
  }
  return new Promise((resolve) => {
    const input = document.createElement('input');
    input.type = 'file';
    if (opts.accept) input.accept = opts.accept;
    if (opts.multiple) input.multiple = true;
    if (opts.capture) input.setAttribute('capture', opts.capture);
    input.style.display = 'none';
    let settled = false;
    const finish = (outcome: PickFilesOutcome) => {
      if (settled) return;
      settled = true;
      input.remove();
      resolve(outcome);
    };
    input.addEventListener('change', () => {
      const files = Array.from(input.files ?? []);
      finish(files.length ? { ok: true, files } : { ok: false, reason: 'cancelled' });
    });
    input.addEventListener('cancel', () => finish({ ok: false, reason: 'cancelled' }));
    document.body.appendChild(input);
    try {
      input.click();
    } catch {
      finish({ ok: false, reason: 'unsupported' });
    }
  });
}
