import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ClaimDetail } from '../../api/models/claim-detail';
import { ClaimsApi } from '../../core/claims-api.service';
import { DEMO_USERS } from '../../core/users';
import { ActionsPanelComponent } from './actions-panel.component';

describe('ActionsPanelComponent', () => {
  const lead = DEMO_USERS.find((u) => u.role === 'LEAD')!;
  const officer = DEMO_USERS.find((u) => u.role === 'OFFICER')!;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionsPanelComponent],
      providers: [provideHttpClient(), { provide: ClaimsApi, useValue: {} }],
    }).compileComponents();
  });

  function claim(status: ClaimDetail['status'], allowedNext: ClaimDetail['allowedNext']): ClaimDetail {
    return {
      id: 'c1', referenceNo: 'CLM-001001', status, allowedNext, version: 3,
      currency: 'SGD', claimedAmount: 1000, reserveAmount: 900, approvedAmount: 900, assigneeId: officer.id,
    };
  }

  async function render(c: ClaimDetail, user = lead) {
    const fixture = TestBed.createComponent(ActionsPanelComponent);
    fixture.componentRef.setInput('claim', c);
    fixture.componentRef.setInput('user', user);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    return {
      sections: [...el.querySelectorAll('h3')].map((h) => h.textContent!.trim()),
      buttons: [...el.querySelectorAll('button')].map((b) => b.textContent!.trim()),
      text: el.textContent ?? '',
    };
  }

  it('hides Settle unless allowedNext contains SETTLED', async () => {
    const underReview = await render(claim('UNDER_REVIEW', ['INFO_REQUESTED', 'APPROVED', 'REJECTED', 'WITHDRAWN']));
    expect(underReview.sections).not.toContain('Settle');
    expect(underReview.buttons).not.toContain('Mark as settled');
    expect(underReview.sections).toContain('Decide');

    const approved = await render(claim('APPROVED', ['SETTLED']));
    expect(approved.sections).toContain('Settle');
    expect(approved.buttons).toContain('Mark as settled');
    expect(approved.sections).not.toContain('Decide');
  });

  it('follows allowedNext, not the status: no Settle when the API does not allow it, even if APPROVED', async () => {
    const view = await render(claim('APPROVED', []));
    expect(view.sections).not.toContain('Settle');
  });

  it('shows an officer a read-only note on a claim assigned to someone else', async () => {
    const other = DEMO_USERS.find((u) => u.role === 'OFFICER' && u.id !== officer.id)!;
    const view = await render(claim('APPROVED', ['SETTLED']), other);
    expect(view.text).toContain('Read only');
    expect(view.buttons).not.toContain('Mark as settled');
  });
});
