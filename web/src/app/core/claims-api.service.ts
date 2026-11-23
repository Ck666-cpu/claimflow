import { Injectable, inject } from '@angular/core';
import { Api } from '../api/api';
import { assign } from '../api/fn/claims/assign';
import { changeReserve } from '../api/fn/claims/change-reserve';
import { decide } from '../api/fn/claims/decide';
import { detail } from '../api/fn/claims/detail';
import { mine } from '../api/fn/claims/mine';
import { requestInformation } from '../api/fn/claims/request-information';
import { respond } from '../api/fn/claims/respond';
import { search, Search$Params } from '../api/fn/claims/search';
import { settle } from '../api/fn/claims/settle';
import { submit } from '../api/fn/claims/submit';
import { withdraw } from '../api/fn/claims/withdraw';
import { exposure } from '../api/fn/metrics/exposure';
import { workload } from '../api/fn/metrics/workload';
import { ClaimDetail } from '../api/models/claim-detail';
import { ClaimSummary } from '../api/models/claim-summary';
import { DecisionRequest } from '../api/models/decision-request';
import { ExposureReport } from '../api/models/exposure-report';
import { SubmitClaimRequest } from '../api/models/submit-claim-request';
import { WorkloadReport } from '../api/models/workload-report';

/**
 * The claims and metrics API, on top of the client generated from docs/api/openapi.yaml (npm run api).
 * Every command takes the version of the claim it was decided on; the response carries the next one.
 */
@Injectable({ providedIn: 'root' })
export class ClaimsApi {
  private readonly api = inject(Api);

  submit(body: SubmitClaimRequest, idempotencyKey: string): Promise<ClaimDetail> {
    return this.api.invoke(submit, { body, 'Idempotency-Key': idempotencyKey });
  }

  mine(): Promise<ClaimSummary[]> {
    return this.api.invoke(mine, {});
  }

  detail(id: string): Promise<ClaimDetail> {
    return this.api.invoke(detail, { id });
  }

  search(params: Search$Params): Promise<ClaimSummary[]> {
    return this.api.invoke(search, params);
  }

  assign(id: string, version: number, assigneeId?: string): Promise<ClaimDetail> {
    return this.api.invoke(assign, { id, body: { version, assigneeId } });
  }

  requestInformation(id: string, version: number, question: string): Promise<ClaimDetail> {
    return this.api.invoke(requestInformation, { id, body: { version, question } });
  }

  respond(id: string, requestId: string, version: number, response: string): Promise<ClaimDetail> {
    return this.api.invoke(respond, { id, requestId, body: { version, response } });
  }

  changeReserve(id: string, version: number, amount: number): Promise<ClaimDetail> {
    return this.api.invoke(changeReserve, { id, body: { version, amount } });
  }

  decide(id: string, body: DecisionRequest): Promise<ClaimDetail> {
    return this.api.invoke(decide, { id, body });
  }

  settle(id: string, version: number): Promise<ClaimDetail> {
    return this.api.invoke(settle, { id, body: { version } });
  }

  withdraw(id: string, version: number): Promise<ClaimDetail> {
    return this.api.invoke(withdraw, { id, body: { version } });
  }

  workload(): Promise<WorkloadReport> {
    return this.api.invoke(workload, {});
  }

  exposure(): Promise<ExposureReport> {
    return this.api.invoke(exposure, {});
  }
}
