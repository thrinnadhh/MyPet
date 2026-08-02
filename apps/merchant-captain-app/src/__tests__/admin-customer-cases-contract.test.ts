import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('admin case queue uses customer case endpoint and explicit refund action', () => {
  const service = source('src/services/admin-operations.ts');
  const screen = source('src/app/admin/cases.tsx');
  assert.match(service, /\/api\/v1\/orders\/customer-cases\/admin/);
  assert.match(service, /issueRefund/);
  assert.match(screen, /Resolve \+ refund/);
  assert.match(screen, /role !== 'ADMIN'/);
});

test('signed content routes expose only HMAC protected reads', () => {
  const gateway = source('../../backend/api-gateway/src/main/kotlin/com/pawsnearme/apigateway/config/SecurityConfig.kt');
  const medicalEdge = source('../../backend/mypet-application/src/main/kotlin/com/pawsnearme/application/edge/MedicalDocumentEdgeSecurityConfiguration.kt');
  const caseEdge = source('../../backend/mypet-application/src/main/kotlin/com/pawsnearme/application/edge/CustomerCaseEvidenceEdgeSecurityConfiguration.kt');
  assert.match(gateway, /medical-documents\/\*\/content/);
  assert.match(gateway, /customer-cases\/evidence\/\*\/content/);
  assert.match(medicalEdge, /AntPathRequestMatcher/);
  assert.match(caseEdge, /AntPathRequestMatcher/);
  assert.doesNotMatch(gateway, /medical-documents\/\*\*/);
});
