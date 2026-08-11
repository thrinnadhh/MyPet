#!/usr/bin/env node
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const specPath = resolve(root, 'contracts/order-lifecycle.json');
const spec = JSON.parse(readFileSync(specPath, 'utf8'));
const checkOnly = process.argv.includes('--check');

const quote = (value) => `'${value}'`;
const tsUnion = (values) => values.map(quote).join(' | ');

const kotlin = `// GENERATED FROM contracts/order-lifecycle.json. DO NOT EDIT BY HAND.\npackage com.pawsnearme.orderservice.model\n\nenum class OrderStatus {\n${spec.orderStatuses.map((status) => `    ${status}`).join(',\n')}\n}\n\nenum class PaymentStatus {\n${spec.paymentStatuses.map((status) => `    ${status}`).join(',\n')}\n}\n\nenum class OrderActor {\n${spec.actors.map((actor) => `    ${actor}`).join(',\n')}\n}\n\ndata class OrderTransition(\n    val actor: OrderActor,\n    val fromStatus: OrderStatus,\n    val toStatus: OrderStatus\n)\n\nobject CanonicalOrderContract {\n    val transitions: Set<OrderTransition> = setOf(\n${spec.transitions.map((transition) => `        OrderTransition(OrderActor.${transition.actor}, OrderStatus.${transition.from}, OrderStatus.${transition.to})`).join(',\n')}\n    )\n\n    fun canTransition(currentStatus: OrderStatus, requestedStatus: OrderStatus, actor: OrderActor): Boolean =\n        OrderTransition(actor, currentStatus, requestedStatus) in transitions\n}\n`;

const merchantQueues = Object.entries(spec.merchantQueues)
  .map(([queue, config]) => {
    const paymentStatuses = config.paymentStatuses
      ? `, paymentStatuses: [${config.paymentStatuses.map(quote).join(', ')}] as PaymentStatus[]`
      : '';
    return `  ${queue}: { statuses: [${config.statuses.map(quote).join(', ')}] as OrderStatus[]${paymentStatuses} },`;
  })
  .join('\n');

const typescript = `// GENERATED FROM contracts/order-lifecycle.json. DO NOT EDIT BY HAND.\nexport const ORDER_STATUSES = [${spec.orderStatuses.map(quote).join(', ')}] as const;\nexport type OrderStatus = ${tsUnion(spec.orderStatuses)};\n\nexport const PAYMENT_STATUSES = [${spec.paymentStatuses.map(quote).join(', ')}] as const;\nexport type PaymentStatus = ${tsUnion(spec.paymentStatuses)};\n\nexport const ORDER_ACTORS = [${spec.actors.map(quote).join(', ')}] as const;\nexport type OrderActor = ${tsUnion(spec.actors)};\n\nexport const ORDER_TRANSITIONS = [\n${spec.transitions.map((transition) => `  { actor: ${quote(transition.actor)}, from: ${quote(transition.from)}, to: ${quote(transition.to)} },`).join('\n')}\n] as const;\n\nexport const MERCHANT_ORDER_QUEUES = {\n${merchantQueues}\n} as const;\n\nexport type MerchantOrderQueue = keyof typeof MERCHANT_ORDER_QUEUES;\n\nexport function canOrderTransition(currentStatus: OrderStatus, requestedStatus: OrderStatus, actor: OrderActor): boolean {\n  return ORDER_TRANSITIONS.some(\n    (transition) => transition.actor === actor && transition.from === currentStatus && transition.to === requestedStatus,\n  );\n}\n`;

const outputs = [
  [
    'backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/model/OrderContractGenerated.kt',
    kotlin,
  ],
  ['apps/customer-app/src/contracts/order-contract.generated.ts', typescript],
  ['apps/merchant-captain-app/src/contracts/order-contract.generated.ts', typescript],
];

let stale = false;
for (const [relativePath, content] of outputs) {
  const absolutePath = resolve(root, relativePath);
  if (checkOnly) {
    let existing = '';
    try {
      existing = readFileSync(absolutePath, 'utf8');
    } catch {
      stale = true;
      console.error(`Missing generated order contract: ${relativePath}`);
      continue;
    }
    if (existing !== content) {
      stale = true;
      console.error(`Stale generated order contract: ${relativePath}`);
    }
  } else {
    mkdirSync(dirname(absolutePath), { recursive: true });
    writeFileSync(absolutePath, content);
    console.log(`Generated ${relativePath}`);
  }
}

if (checkOnly && stale) {
  console.error('Run: node scripts/generate-order-contracts.mjs');
  process.exit(1);
}

if (checkOnly) console.log('Order contract projections are current.');
