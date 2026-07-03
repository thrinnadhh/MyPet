package com.pawsnearme.chatservice.repository

import com.pawsnearme.chatservice.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ConversationRepository : JpaRepository<Conversation, UUID> {
    fun findByContextTypeAndContextId(contextType: ChatContextType, contextId: UUID): Conversation?

    fun findByCustomerIdOrderByUpdatedAtDesc(customerId: UUID): List<Conversation>

    fun findByProviderIdOrderByUpdatedAtDesc(providerId: UUID): List<Conversation>
}

interface MessageRepository : JpaRepository<Message, UUID> {
    fun findByConversationIdAndSentAtAfterOrderBySentAtAsc(
        conversationId: UUID,
        sentAt: Instant
    ): List<Message>

    fun findByConversationIdOrderBySentAtAsc(conversationId: UUID): List<Message>

    @Modifying
    @Query(
        """
        UPDATE Message m
        SET m.readAt = :readAt
        WHERE m.conversationId = :conversationId
          AND m.senderId <> :readerId
          AND m.readAt IS NULL
        """
    )
    fun markUnreadAsRead(
        @Param("conversationId") conversationId: UUID,
        @Param("readerId") readerId: UUID,
        @Param("readAt") readAt: Instant
    ): Int
}

interface ProfileRefRepository : JpaRepository<ProfileRef, UUID>

interface ProviderRefRepository : JpaRepository<ProviderRef, UUID> {
    fun findByOwnerUserId(ownerUserId: UUID): List<ProviderRef>
}
