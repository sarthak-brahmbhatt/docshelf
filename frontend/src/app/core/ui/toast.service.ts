// Thin MatSnackBar wrapper for app-wide info/success/error toasts (used by the error interceptor and features).
import { Injectable, inject } from '@angular/core';
import { MatSnackBar, MatSnackBarRef, TextOnlySnackBar } from '@angular/material/snack-bar';

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly snackBar = inject(MatSnackBar);

  info(message: string, action = 'OK', durationMs = 4000): MatSnackBarRef<TextOnlySnackBar> {
    return this.snackBar.open(message, action, { duration: durationMs });
  }

  success(message: string, durationMs = 3000): MatSnackBarRef<TextOnlySnackBar> {
    return this.snackBar.open(message, undefined, { duration: durationMs, panelClass: 'toast-success' });
  }

  error(message: string, action = 'Dismiss', durationMs = 7000): MatSnackBarRef<TextOnlySnackBar> {
    return this.snackBar.open(message, action, { duration: durationMs, panelClass: 'toast-error' });
  }
}
