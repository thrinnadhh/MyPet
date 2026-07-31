#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if old not in text:
        raise RuntimeError(f"Expected block not found in {path}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1))


models = Path("backend/payment-service/src/main/kotlin/com/pawsnearme/paymentservice/model/Models.kt")
service = Path("backend/payment-service/src/main/kotlin/com/pawsnearme/paymentservice/service/PaymentService.kt")
tests = Path("backend/payment-service/src/test/kotlin/com/pawsnearme/paymentservice/service/PaymentServiceTests.kt")

replace_once(
    models,
    '''@Entity
@Table(name = "linked_accounts", schema = "payments")
class LinkedAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "linked_account_id")
    var linkedAccountId: UUID? = null,

    @Column(name = "payee_user_id", nullable = false, unique = true)
    var payeeUserId: UUID,

    @Column(name = "payee_role", nullable = false)
    var payeeRole: String,

    @Column(name = "account_number", nullable = false)
    @JsonIgnore
    var accountNumber: String,

    @Column(name = "ifsc", nullable = false)
    @JsonIgnore
    var ifsc: String,

    @Column(name = "business_name", nullable = false)
    var businessName: String,

    @Column(name = "email", nullable = false)
    var email: String,

    @Column(name = "razorpay_account_id", nullable = false)
    var razorpayAccountId: String,

    @Column(name = "pending_clawback_balance", nullable = false)
    var pendingClawbackBalance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
''',
    '''@Entity
@Table(name = "linked_accounts", schema = "payments")
class LinkedAccount(
    @Id
    @Column(name = "payee_user_id")
    var payeeUserId: UUID,

    @Column(name = "payee_role", nullable = false)
    var payeeRole: String,

    /** Razorpay-owned token/reference. Full bank coordinates are never stored locally. */
    @Column(name = "razorpay_account_id", nullable = false)
    var razorpayAccountId: String,

    @Column(name = "kyc_status", nullable = false)
    var kycStatus: String,

    @Column(name = "pending_clawback_balance", nullable = false)
    var pendingClawbackBalance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
''',
)

replace_once(
    service,
    '''        val existing = linkedAccountRepository.findByPayeeUserId(req.payeeUserId)
        if (existing != null) {
            existing.payeeRole = req.payeeRole
            existing.accountNumber = req.accountNumber
            existing.ifsc = req.ifsc
            existing.businessName = req.businessName
            existing.email = req.email
            return linkedAccountRepository.save(existing)
        }

        val razorpayAccId = "acc_mock_${UUID.randomUUID().toString().take(12)}"
        val account = LinkedAccount(
            payeeUserId = req.payeeUserId,
            payeeRole = req.payeeRole,
            accountNumber = req.accountNumber,
            ifsc = req.ifsc,
            businessName = req.businessName,
            email = req.email,
            razorpayAccountId = razorpayAccId
        )
''',
    '''        require(req.accountNumber.matches(Regex("[0-9]{6,34}"))) {
            "Account number must contain 6-34 digits"
        }
        require(req.ifsc.matches(Regex("[A-Z]{4}0[A-Z0-9]{6}"))) {
            "IFSC format is invalid"
        }
        val existing = linkedAccountRepository.findByPayeeUserId(req.payeeUserId)
        if (existing != null) {
            existing.payeeRole = req.payeeRole
            existing.kycStatus = "MOCK_ONLY"
            return linkedAccountRepository.save(existing)
        }

        val razorpayAccId = "acc_mock_${UUID.randomUUID().toString().take(12)}"
        val account = LinkedAccount(
            payeeUserId = req.payeeUserId,
            payeeRole = req.payeeRole,
            razorpayAccountId = razorpayAccId,
            kycStatus = "MOCK_ONLY"
        )
''',
)

old_constructor = 'LinkedAccount(payeeUserId = ownerId, payeeRole = "MERCHANT", accountNumber = "123456", ifsc = "UTIB0001", businessName = "Store", email = "m@store.com", razorpayAccountId = "acc_123", pendingClawbackBalance = BigDecimal("100.00"))'
new_constructor = 'LinkedAccount(payeeUserId = ownerId, payeeRole = "MERCHANT", razorpayAccountId = "acc_123", kycStatus = "ACTIVE", pendingClawbackBalance = BigDecimal("100.00"))'
replace_once(tests, old_constructor, new_constructor)

old_constructor = 'LinkedAccount(payeeUserId = payeeId, payeeRole = "MERCHANT", accountNumber = "123456", ifsc = "UTIB0001", businessName = "Store", email = "m@store.com", razorpayAccountId = "acc_123", pendingClawbackBalance = BigDecimal.ZERO)'
new_constructor = 'LinkedAccount(payeeUserId = payeeId, payeeRole = "MERCHANT", razorpayAccountId = "acc_123", kycStatus = "ACTIVE", pendingClawbackBalance = BigDecimal.ZERO)'
replace_once(tests, old_constructor, new_constructor)

print("Linked account entity aligned to Flyway schema; bank coordinates removed from persistence")
