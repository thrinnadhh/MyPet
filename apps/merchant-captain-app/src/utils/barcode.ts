const CONTROL_CHARACTER_PATTERN = /[\u0000-\u001f\u007f]/;
const PRINTABLE_ASCII_PATTERN = /^[\x20-\x7e]+$/;

/**
 * Normalizes scanner and hardware-wedge output into one stable catalog key.
 *
 * Android scanners commonly report UPC-A as EAN-13 with a leading zero while
 * iOS may report the 12 digit UPC-A value. Treating those forms as aliases keeps
 * one product record portable across devices.
 */
export function normalizeBarcode(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) return '';

  if (/^[\d\s]+$/.test(trimmed)) {
    const digits = trimmed.replace(/\s+/g, '');
    return digits.length === 13 && digits.startsWith('0') ? digits.slice(1) : digits;
  }

  return trimmed.replace(/\s+/g, ' ').toUpperCase();
}

export function barcodeValidationMessage(value: string): string | undefined {
  const normalized = normalizeBarcode(value);
  if (!normalized) return undefined;
  if (CONTROL_CHARACTER_PATTERN.test(normalized) || !PRINTABLE_ASCII_PATTERN.test(normalized)) {
    return 'Barcode may contain only printable letters, numbers and symbols.';
  }
  if (normalized.length < 3 || normalized.length > 50) {
    return 'Barcode must contain between 3 and 50 characters.';
  }
  return undefined;
}

export function barcodeLookupCandidates(value: string): string[] {
  const normalized = normalizeBarcode(value);
  if (!normalized) return [];

  const candidates = new Set<string>([normalized]);
  if (/^\d{12}$/.test(normalized)) candidates.add(`0${normalized}`);
  return [...candidates];
}
