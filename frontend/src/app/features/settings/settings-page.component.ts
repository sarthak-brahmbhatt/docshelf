// Settings page: API base URL + token (persisted locally), connection test via GET /system/health, voice language, platform info.
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { SystemHealth } from '../../core/api/models';
import { NotificationService } from '../../core/platform/notification.service';
import { PlatformService } from '../../core/platform/platform.service';
import { RecorderService } from '../../core/platform/recorder.service';
import { SpeechService } from '../../core/platform/speech.service';
import { SettingsService, VOICE_LANGUAGES } from '../../core/settings/settings.service';
import { ToastService } from '../../core/ui/toast.service';
import { ErrorBannerComponent, LoadingState, errorMessage, failed, idle, loading, ready } from '../../shared';

@Component({
  selector: 'app-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatProgressSpinnerModule,
    ErrorBannerComponent,
  ],
  templateUrl: './settings-page.component.html',
  styleUrl: './settings-page.component.scss',
})
export class SettingsPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  protected readonly settings = inject(SettingsService);
  protected readonly platform = inject(PlatformService);
  protected readonly speech = inject(SpeechService);
  protected readonly recorder = inject(RecorderService);
  protected readonly notifications = inject(NotificationService);

  protected readonly languages = VOICE_LANGUAGES;
  protected readonly info = this.platform.info();

  protected readonly form = this.fb.nonNullable.group({
    apiBaseUrl: ['', [Validators.pattern(/^(https?:\/\/)?[^\s/]+(:\d+)?(\/.*)?$|^$/)]],
    apiToken: [''],
    voiceLanguage: ['en-IN', Validators.required],
  });

  protected readonly showToken = signal(false);
  protected readonly saving = signal(false);
  protected readonly health = signal<LoadingState<SystemHealth>>(idle());
  protected readonly healthChecks = computed(() => {
    const h = this.health().data;
    if (!h) return [];
    return [
      { label: 'Database', ok: h.db?.status === 'UP', detail: h.db?.detail },
      { label: 'Blob volume', ok: h.blobVolume?.status === 'UP', detail: h.blobVolume?.detail },
      { label: 'OpenAI key present', ok: h.openaiKeyPresent, detail: h.openaiKeyPresent ? null : 'LLM features degrade to rule-only' },
      { label: 'SMTP', ok: h.smtp?.status === 'UP', detail: h.smtp?.detail },
      { label: 'Last NAV refresh', ok: !!h.lastNavRefresh, detail: h.lastNavRefresh ?? 'never' },
    ];
  });

  /** Set when redirected here by the error interceptor after a 401. */
  protected readonly unauthorizedHint = signal(false);

  constructor() {
    // Populate the form once persisted settings are loaded (they usually already are, via the app initializer).
    effect(() => {
      if (this.settings.loaded()) {
        this.form.patchValue(
          {
            apiBaseUrl: this.settings.apiBaseUrl(),
            apiToken: this.settings.apiToken(),
            voiceLanguage: this.settings.voiceLanguage(),
          },
          { emitEvent: false },
        );
      }
    });
    this.unauthorizedHint.set(this.route.snapshot.queryParamMap.get('reason') === 'unauthorized');
  }

  async save(): Promise<void> {
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    try {
      const { apiBaseUrl, apiToken, voiceLanguage } = this.form.getRawValue();
      await this.settings.setApiBaseUrl(apiBaseUrl);
      await this.settings.setApiToken(apiToken);
      await this.settings.setVoiceLanguage(voiceLanguage);
      this.form.patchValue({ apiBaseUrl: this.settings.apiBaseUrl() }, { emitEvent: false });
      this.form.markAsPristine();
      this.toast.success('Settings saved');
      this.unauthorizedHint.set(false);
      // Best-effort mirror of the voice language to the server (ignored when the server is unreachable).
      this.api.updateUserSettings({ voiceLanguage }).subscribe({ error: () => undefined });
    } catch (e) {
      this.toast.error(errorMessage(e));
    } finally {
      this.saving.set(false);
    }
  }

  async testConnection(): Promise<void> {
    if (this.form.dirty) {
      await this.save();
    }
    this.health.set(loading(this.health().data));
    try {
      const result = await firstValueFrom(this.api.getSystemHealth({ silent: true }));
      this.health.set(ready(result));
      this.toast.success(`Connected — server is ${result.status}`);
    } catch (e) {
      this.health.set(failed(e, this.health().data));
    }
  }

  clearToken(): void {
    this.form.patchValue({ apiToken: '' });
    this.form.markAsDirty();
  }

  async requestNotificationPermission(): Promise<void> {
    const granted = await this.notifications.requestPermission();
    this.toast.info(granted ? 'Notifications allowed' : 'Notifications not allowed');
  }
}
