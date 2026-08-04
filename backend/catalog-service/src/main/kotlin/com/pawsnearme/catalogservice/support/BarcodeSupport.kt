package com.pawsnearme.catalogservice.support

object BarcodeSupport {
    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = if (trimmed.all { it.isDigit() || it.isWhitespace() }) {
            trimmed.filterNot { it.isWhitespace() }
        } else {
            trimmed.replace(Regex("\\s+"), " ").uppercase()
        }.let { value ->
            if (value.length == 13 && value.startsWith('0') && value.all(Char::isDigit)) {
                value.substring(1)
            } else {
                value
            }
        }

        require(normalized.length in 3..50) {
            "Barcode must contain between 3 and 50 characters"
        }
        require(normalized.all { character -> character.code in 0x20..0x7E }) {
            "Barcode may contain only printable letters, numbers and symbols"
        }
        return normalized
    }

    fun requireBarcode(raw: String?): String =
        normalize(raw) ?: throw IllegalArgumentException("Barcode is required")

    fun lookupCandidates(raw: String): List<String> {
        val normalized = requireBarcode(raw)
        return buildList {
            add(normalized)
            if (normalized.length == 12 && normalized.all(Char::isDigit)) {
                add("0$normalized")
            }
        }.distinct()
    }
}

class BarcodeConflictException(message: String) : IllegalStateException(message)
