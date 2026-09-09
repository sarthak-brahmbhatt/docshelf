// Generic yes/no MatDialog; open with `confirm(dialog, {...})` which resolves true when the user confirms.
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';

export interface ConfirmDialogData {
  title: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Style the confirm button as destructive. */
  destructive?: boolean;
}

@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    @if (data.message) {
      <mat-dialog-content>{{ data.message }}</mat-dialog-content>
    }
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="ref.close(false)">{{ data.cancelLabel || 'Cancel' }}</button>
      <button
        mat-flat-button
        type="button"
        [class.destructive]="data.destructive"
        cdkFocusInitial
        (click)="ref.close(true)"
      >
        {{ data.confirmLabel || 'Confirm' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .destructive { --mdc-filled-button-container-color: var(--mat-sys-error); --mdc-filled-button-label-text-color: var(--mat-sys-on-error); }
  `,
})
export class ConfirmDialogComponent {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  protected readonly ref = inject(MatDialogRef<ConfirmDialogComponent, boolean>);
}

/** Opens the confirm dialog and resolves with the user's choice (false when dismissed). */
export async function confirm(dialog: MatDialog, data: ConfirmDialogData): Promise<boolean> {
  const ref = dialog.open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, {
    data,
    maxWidth: '420px',
    autoFocus: false,
  });
  return (await firstValueFrom(ref.afterClosed())) === true;
}
