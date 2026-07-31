package com.pawsnearme.captainservice.security

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Encrypts legacy plaintext bank fields in place after deployment.
 * The operation is idempotent because already-versioned ciphertext is skipped.
 */
@Component
class LegacyBankDataMigration(
    private val jdbcTemplate: JdbcTemplate,
    private val bankDataCipher: BankDataCipher,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val rows = jdbcTemplate.query(
            """
                SELECT captain_id, bank_account, bank_ifsc
                FROM captains.captain_profiles
                WHERE (bank_account IS NOT NULL AND bank_account NOT LIKE 'v1:%')
                   OR (bank_ifsc IS NOT NULL AND bank_ifsc NOT LIKE 'v1:%')
            """.trimIndent(),
        ) { resultSet, _ ->
            LegacyBankRow(
                captainId = resultSet.getObject("captain_id", UUID::class.java),
                bankAccount = resultSet.getString("bank_account"),
                bankIfsc = resultSet.getString("bank_ifsc"),
            )
        }

        rows.forEach { row ->
            jdbcTemplate.update(
                """
                    UPDATE captains.captain_profiles
                    SET bank_account = ?, bank_ifsc = ?
                    WHERE captain_id = ?
                """.trimIndent(),
                bankDataCipher.encrypt(row.bankAccount),
                bankDataCipher.encrypt(row.bankIfsc),
                row.captainId,
            )
        }

        if (rows.isNotEmpty()) {
            log.info("Encrypted legacy bank fields for {} captain profile(s)", rows.size)
        }
    }

    private data class LegacyBankRow(
        val captainId: UUID,
        val bankAccount: String?,
        val bankIfsc: String?,
    )
}
