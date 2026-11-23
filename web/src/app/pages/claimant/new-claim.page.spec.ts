import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ClaimsApi } from '../../core/claims-api.service';
import { todayIso } from '../../shared/validators';
import { NewClaimPage } from './new-claim.page';

describe('NewClaimPage', () => {
  const api = { submit: vi.fn() };

  beforeEach(async () => {
    api.submit.mockReset();
    await TestBed.configureTestingModule({
      imports: [NewClaimPage],
      providers: [provideRouter([]), provideHttpClient(), { provide: ClaimsApi, useValue: api }],
    }).compileComponents();
  });

  function tomorrow(): string {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    return todayIso(d);
  }

  async function render() {
    const fixture = TestBed.createComponent(NewClaimPage);
    await fixture.whenStable();
    const page = fixture.componentInstance;
    page.form.setValue({
      type: 'MOTOR',
      marketCode: 'SG',
      policyNumber: 'POL-SG-1',
      incidentDate: todayIso(),
      description: 'Rear-ended at a junction.',
      claimedAmount: 100,
    });
    return { fixture, page, el: fixture.nativeElement as HTMLElement };
  }

  function errors(el: HTMLElement): string[] {
    return [...el.querySelectorAll('.field-error')].map((e) => e.textContent!.trim());
  }

  it('rejects an incident date in the future and does not submit', async () => {
    const { fixture, page, el } = await render();
    page.form.controls.incidentDate.setValue(tomorrow());

    await page.submit();
    await fixture.whenStable();

    expect(page.form.controls.incidentDate.hasError('future')).toBe(true);
    expect(errors(el)).toContain('The incident date cannot be in the future.');
    expect(api.submit).not.toHaveBeenCalled();
  });

  it('rejects a zero amount and does not submit', async () => {
    const { fixture, page, el } = await render();
    page.form.controls.claimedAmount.setValue(0);

    await page.submit();
    await fixture.whenStable();

    expect(page.form.controls.claimedAmount.hasError('min')).toBe(true);
    expect(errors(el)).toContain('The amount must be greater than zero.');
    expect(api.submit).not.toHaveBeenCalled();
  });

  it('accepts today and a positive amount', async () => {
    const { page } = await render();
    expect(page.form.valid).toBe(true);
  });
});
