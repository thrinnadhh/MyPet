#!/usr/bin/env node
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const spec = JSON.parse(readFileSync(resolve(root, 'contracts/checkout-pricing.json'), 'utf8'));
const checkOnly = process.argv.includes('--check');

const kotlin = `// GENERATED FROM contracts/checkout-pricing.json. DO NOT EDIT BY HAND.\npackage com.pawsnearme.orderservice.service\n\nimport java.math.BigDecimal\n\nobject CheckoutPricingContract {\n    val TAX_RATE: BigDecimal = BigDecimal("${spec.taxRate}")\n    val BASE_DELIVERY_FEE: BigDecimal = BigDecimal("${spec.delivery.baseFee.toFixed(2)}")\n    val INCLUDED_DISTANCE_KM: BigDecimal = BigDecimal("${spec.delivery.includedDistanceKm.toFixed(1)}")\n    val PER_KM_FEE: BigDecimal = BigDecimal("${spec.delivery.perKmFee.toFixed(2)}")\n    val MAX_SERVICE_DISTANCE_KM: BigDecimal = BigDecimal("${spec.delivery.maxServiceDistanceKm.toFixed(1)}")\n    val ROUTE_DISTANCE_FACTOR: BigDecimal = BigDecimal("${spec.delivery.routeDistanceFactor.toFixed(2)}")\n}\n`;

const typescript = `// GENERATED FROM contracts/checkout-pricing.json. DO NOT EDIT BY HAND.\nexport const CHECKOUT_TAX_RATE = ${spec.taxRate};\nexport const DELIVERY_BASE_FEE = ${spec.delivery.baseFee};\nexport const DELIVERY_INCLUDED_DISTANCE_KM = ${spec.delivery.includedDistanceKm};\nexport const DELIVERY_PER_KM_FEE = ${spec.delivery.perKmFee};\nexport const DELIVERY_MAX_SERVICE_DISTANCE_KM = ${spec.delivery.maxServiceDistanceKm};\nexport const DELIVERY_ROUTE_DISTANCE_FACTOR = ${spec.delivery.routeDistanceFactor};\n`;

const outputs = [
  ['backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/CheckoutPricingContract.kt', kotlin],
  ['apps/customer-app/src/contracts/checkout-pricing.generated.ts', typescript],
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
      console.error(`Missing generated checkout pricing contract: ${relativePath}`);
      continue;
    }
    if (existing !== content) {
      stale = true;
      console.error(`Stale generated checkout pricing contract: ${relativePath}`);
    }
  } else {
    mkdirSync(dirname(absolutePath), { recursive: true });
    writeFileSync(absolutePath, content);
    console.log(`Generated ${relativePath}`);
  }
}

if (checkOnly && stale) {
  console.error('Run: node scripts/generate-checkout-pricing-contracts.mjs');
  process.exit(1);
}
if (checkOnly) console.log('Checkout pricing contract projections are current.');
