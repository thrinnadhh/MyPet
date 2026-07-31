package com.pawsnearme.captainservice.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class BankDataCipher(
    @Value("\${security.bank-data.encryption-key}") encodedKey: String,
) {
    companion object {
        private const val PREFIX = "v1:"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
    }

    private val secureRandom = SecureRandom()
    private val key = Base64.getDecoder().decode(encodedKey).also {
        require(it.size == 32) {
            "BANK_DATA_ENCRYPTION_KEY must be a Base64-encoded 256-bit key"
        }
    }

    fun encrypt(value: String?): String? {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (isEncrypted(normalized)) return normalized

        val iv = ByteArray(IV_LENGTH).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_LENGTH_BITS, iv),
        )
        val encrypted = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decrypt(value: String?): String? {
        val stored = value?.takeIf { it.isNotBlank() } ?: return null
        if (!isEncrypted(stored)) return stored

        val payload = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
        require(payload.size > IV_LENGTH) { "Encrypted bank data is malformed" }
        val iv = payload.copyOfRange(0, IV_LENGTH)
        val ciphertext = payload.copyOfRange(IV_LENGTH, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_LENGTH_BITS, iv),
        )
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    fun isEncrypted(value: String?): Boolean = value?.startsWith(PREFIX) == true
}
