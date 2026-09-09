// Unit tests for MaskedValueComponent: reveal via callback, 30 s countdown then re-mask, error surfacing.
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MaskedValueComponent } from './masked-value.component';

describe('MaskedValueComponent', () => {
  let fixture: ComponentFixture<MaskedValueComponent>;
  let component: MaskedValueComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MaskedValueComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(MaskedValueComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('masked', 'XXXX XXXX 1234');
    fixture.componentRef.setInput('label', 'Aadhaar number');
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
  });

  function text(): string {
    return (fixture.nativeElement as HTMLElement).querySelector('.value')!.textContent!.trim();
  }

  it('shows the masked value and a reveal button when a reveal fn is provided', () => {
    fixture.componentRef.setInput('reveal', () => of('1234 5678 9012'));
    fixture.detectChanges();
    expect(text()).toBe('XXXX XXXX 1234');
    expect((fixture.nativeElement as HTMLElement).querySelector('button[aria-label="Reveal Aadhaar number"]')).not.toBeNull();
  });

  it('hides the reveal button when no reveal fn is given', () => {
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('button')).toBeNull();
  });

  it('reveals the value from an observable and re-masks after the display period', async () => {
    vi.useFakeTimers();
    fixture.componentRef.setInput('reveal', () => of({ value: '1234 5678 9012', displaySeconds: 30 }));
    fixture.detectChanges();
    await component.show();
    fixture.detectChanges();
    expect(component.revealed()).toBe(true);
    expect(text()).toBe('1234 5678 9012');
    expect(component.secondsLeft()).toBe(30);

    vi.advanceTimersByTime(29_000);
    expect(component.revealed()).toBe(true);
    expect(component.secondsLeft()).toBe(1);

    vi.advanceTimersByTime(1_000);
    fixture.detectChanges();
    expect(component.revealed()).toBe(false);
    expect(component.value()).toBe('');
    expect(text()).toBe('XXXX XXXX 1234');
  });

  it('accepts a promise-returning reveal fn and uses the default display seconds', async () => {
    vi.useFakeTimers();
    fixture.componentRef.setInput('displaySeconds', 5);
    fixture.componentRef.setInput('reveal', () => Promise.resolve('ABCDE1234F'));
    fixture.detectChanges();
    await component.show();
    expect(component.revealed()).toBe(true);
    expect(component.secondsLeft()).toBe(5);
    vi.advanceTimersByTime(5_000);
    expect(component.revealed()).toBe(false);
  });

  it('re-masks immediately on hide() and stays masked afterwards', async () => {
    vi.useFakeTimers();
    fixture.componentRef.setInput('reveal', () => of('1234 5678 9012'));
    fixture.detectChanges();
    await component.show();
    expect(component.revealed()).toBe(true);
    component.hide();
    expect(component.revealed()).toBe(false);
    vi.advanceTimersByTime(60_000);
    expect(component.revealed()).toBe(false);
    expect(component.error()).toBeNull();
  });

  it('surfaces reveal errors without revealing', async () => {
    fixture.componentRef.setInput('reveal', () => throwError(() => new Error('Not sensitive')));
    fixture.detectChanges();
    await component.show();
    expect(component.revealed()).toBe(false);
    expect(component.error()).toBe('Not sensitive');
  });
});
