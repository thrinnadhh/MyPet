from pathlib import Path

path = Path('apps/merchant-captain-app/src/app/delivery.tsx')
content = path.read_text(encoding='utf-8')
needle = "type DeliveryStep = 1 | 2 | 3 | 4;\n\nexport default function DeliveryScreen()"
replacement = """type DeliveryStep = 1 | 2 | 3 | 4;

function shortOrderId(orderId: string): string {
  return orderId.slice(0, 8).toUpperCase();
}

async function responseError(response: Response, fallback: string): Promise<string> {
  const body = (await response.json().catch(() => null)) as { error?: string; message?: string } | null;
  return body?.error ?? body?.message ?? fallback;
}

export default function DeliveryScreen()"""
if content.count(needle) != 1:
    raise RuntimeError(f'Expected one delivery helper insertion point, found {content.count(needle)}')
path.write_text(content.replace(needle, replacement, 1), encoding='utf-8')
print('Captain delivery helpers restored.')
