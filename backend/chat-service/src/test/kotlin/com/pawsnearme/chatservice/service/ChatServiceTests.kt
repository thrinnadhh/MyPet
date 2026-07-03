package com.pawsnearme.chatservice.service

import com.pawsnearme.chatservice.dto.UpdateConversationPrivacyRequest
import com.pawsnearme.chatservice.model.*
import com.pawsnearme.chatservice.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.kafka.core.KafkaTemplate
import java.util.Optional
import java.util.UUID

class ChatServiceTests {

    private val conversationRepository: ConversationRepository = mock()
    private val messageRepository: MessageRepository = mock()
    private val profileRefRepository: ProfileRefRepository = mock()
    private val providerRefRepository: ProviderRefRepository = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val service = ChatService(
        conversationRepository,
        messageRepository,
        profileRefRepository,
        providerRefRepository,
        kafkaTemplate
    )

    private val customerId = UUID.randomUUID()
    private val merchantId = UUID.randomUUID()
    private val providerId = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()

    private fun provider() = ProviderRef(
        providerId = providerId,
        ownerUserId = merchantId,
        providerType = "VET_HOSPITAL",
        name = "VetCare Plus"
    )

    private fun conversation() = Conversation(
        conversationId = conversationId,
        customerId = customerId,
        providerId = providerId,
        contextType = ChatContextType.APPOINTMENT,
        contextId = UUID.randomUUID(),
        providerType = "VET_HOSPITAL"
    )

    @Test
    fun `updatePrivacy - merchant can reveal customer phone`() {
        val conversation = conversation()
        whenever(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation))
        whenever(providerRefRepository.findById(providerId)).thenReturn(Optional.of(provider()))
        whenever(conversationRepository.save(any())).thenAnswer { it.arguments[0] as Conversation }
        whenever(messageRepository.findByConversationIdOrderBySentAtAsc(conversationId)).thenReturn(emptyList())
        whenever(profileRefRepository.findById(customerId)).thenReturn(
            Optional.of(ProfileRef(customerId, "Priya", "9999999999"))
        )
        whenever(profileRefRepository.findById(merchantId)).thenReturn(
            Optional.of(ProfileRef(merchantId, "Clinic Admin", "8888888888"))
        )

        val result = service.updatePrivacy(
            conversationId,
            UpdateConversationPrivacyRequest(customerPhoneVisible = true),
            merchantId,
            "MERCHANT"
        )

        assertTrue(result.privacy.customerPhoneVisible)
        assertEquals("9999999999", result.customer.phoneNumber)
    }

    @Test
    fun `updatePrivacy - customer cannot change privacy`() {
        whenever(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation()))

        val ex = assertThrows<IllegalArgumentException> {
            service.updatePrivacy(
                conversationId,
                UpdateConversationPrivacyRequest(customerPhoneVisible = true),
                customerId,
                "CUSTOMER"
            )
        }
        assertTrue(ex.message!!.contains("Only merchants"))
    }

    @Test
    fun `getConversation - customer does not see doctor phone when hidden`() {
        val conversation = conversation()
        val doctorId = UUID.randomUUID()
        conversation.assignedDoctorUserId = doctorId
        conversation.doctorPhoneVisible = false

        whenever(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation))
        whenever(providerRefRepository.findById(providerId)).thenReturn(Optional.of(provider()))
        whenever(messageRepository.findByConversationIdOrderBySentAtAsc(conversationId)).thenReturn(emptyList())
        whenever(profileRefRepository.findById(customerId)).thenReturn(
            Optional.of(ProfileRef(customerId, "Priya", "9999999999"))
        )
        whenever(profileRefRepository.findById(merchantId)).thenReturn(
            Optional.of(ProfileRef(merchantId, "Clinic Admin", "8888888888"))
        )
        whenever(profileRefRepository.findById(doctorId)).thenReturn(
            Optional.of(ProfileRef(doctorId, "Anita Rao", "7777777777"))
        )

        val result = service.getConversation(conversationId, customerId, "CUSTOMER")

        assertNotNull(result.doctor)
        assertTrue(result.doctor!!.phoneHidden)
        assertNull(result.doctor.phoneNumber)
    }
}
