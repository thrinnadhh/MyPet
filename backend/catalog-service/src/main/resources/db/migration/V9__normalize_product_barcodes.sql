-- Canonicalize scanner values so UPC-A and leading-zero EAN-13 reads resolve
-- to the same provider-scoped product on Android and iOS.
DROP INDEX IF EXISTS catalog.idx_offerings_provider_barcode;

UPDATE catalog.offerings
SET barcode = CASE
    WHEN trim(barcode) ~ '^[0-9[:space:]]+$'
        THEN regexp_replace(trim(barcode), '[[:space:]]+', '', 'g')
    ELSE upper(regexp_replace(trim(barcode), '[[:space:]]+', ' ', 'g'))
END
WHERE barcode IS NOT NULL;

UPDATE catalog.offerings
SET barcode = substring(barcode FROM 2)
WHERE barcode ~ '^0[0-9]{12}$';

UPDATE catalog.offerings
SET barcode = NULL
WHERE barcode = '';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM catalog.offerings
        WHERE barcode IS NOT NULL
        GROUP BY provider_id, barcode
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Duplicate provider barcode values remain after canonicalization; resolve conflicting offerings before applying V9';
    END IF;
END
$$;

ALTER TABLE catalog.offerings
    DROP CONSTRAINT IF EXISTS chk_offerings_barcode_length;

ALTER TABLE catalog.offerings
    ADD CONSTRAINT chk_offerings_barcode_length
    CHECK (barcode IS NULL OR char_length(barcode) BETWEEN 3 AND 50);

CREATE UNIQUE INDEX idx_offerings_provider_barcode
    ON catalog.offerings (provider_id, barcode)
    WHERE barcode IS NOT NULL;
