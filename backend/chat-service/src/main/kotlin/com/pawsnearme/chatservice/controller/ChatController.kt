package com.pawsnearme.chatservice.controller

import com.pawsnearme.chatservice.dto.*
import com.pawsnearme.chatservice.service.ChatAttachmentService
import com.pawsnearme.chatservice.service.ChatService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/chat")
class ChatController(
    private val chatService: ChatService,
    private val chatAttachmentService: ChatAttachmentService
) {

    @PostMapping("/conversations")
    fun createConversation(
        @Valid @RequestBody request: CreateConversationRequest,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) userRole: String?
    ): ResponseEntity<Any> {
        val caller = requireCaller(userId, userRole) ?: return unauthorized()
        val conversation = chatService.createOrGetConversation(request, caller.first, caller.second)
        return ResponseEntity.status(HttpStatus.CREATED).body(conversation)
    }

    @GetMapping("/conversations")
    fun listConversations(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) userRole: String?
    ): ResponseEntity<Any> {
        val caller = requireCaller(userId, userRole) ?: return unauthorized()
        return ResponseEntity.ok(chatService.listConversations(caller.first, caller.second))
    }

    @GetMapping("/conversations/{conversationId}")
    fun getConversation(
        @PathVariable conversationId: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) userRole: String?
    ): ResponseEntity<Any> {
        val caller = requireCaller(userId, userRole) ?: return unauthorized()
        return ResponseEntity.ok(chatService.getConversation(conversationId, caller.first, caller.second))
    }

    @GetMapping("/conversations/{conversationId}/messages")
    fun listMessages(
        @PathVariable conversationId: UUID,
        @RequestParam(required = false) after: Instant?,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) userRole: String?
    ): ResponseEntity<Any> {
        val caller = requireCaller(userId, userRole) ?: return unauthorized()
        return ResponseEntity.ok(chatService.listMessages(conversationId, caller.first, caller.second, after))
    }

    @PostMapping("/conversations/{conversationId}/messages")
    fun sendMessage(
        @PathVariable conversationId: UUID,
        @Valid @RequestBody request: SendMessageRequest,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) userRole: String?
    ): ResponseEntity<Any> {
        val caller = requireCaller(userId, userRole) ?: return unauthorized()
        val message = chatService.sendMessage(conversationId, request, caller.first, caller.second)
        return ResponseEntity.status(HttpStatus.CREATED).body(message)
    }

    @PatchMapping("/conversations/{conversationId}/privacy")
    fun updatePrivacy(
        @PathVariable conversationId: UUID,
        @Valid @RequestBody request: UpdateConversationPrivacyRequest,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) userRole: String?
    ): ResponseEntity<Any> {
        val caller = requireCaller(userId, userRole) ?: return unauthorized()
        return ResponseEntity.ok(chatService.updatePrivacy(conversationId, request, caller.first, caller.second))
    }

    @PostMapping("/conversations/{conversationId}/read")
    fun markRead(
        @PathVariable conversationId: UUID,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) userRole: String?
    ): ResponseEntity<Any> {
        val caller = requireCaller(userId, userRole) ?: return unauthorized()
        chatService.markAsRead(conversationId, caller.first, caller.second)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/attachments")
    fun uploadAttachment(
        @RequestParam("file") file: MultipartFile,
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestHeader("X-User-Role", required = false) userRole: String?
    ): ResponseEntity<Any> {
        if (requireCaller(userId, userRole) == null) return unauthorized()
        val (imageUrl, mimeType) = chatAttachmentService.uploadImage(file)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            AttachmentUploadResponse(imageUrl = imageUrl, imageMimeType = mimeType)
        )
    }

    private fun requireCaller(userId: String?, userRole: String?): Pair<UUID, String>? {
        if (userId.isNullOrBlank()) return null
        return UUID.fromString(userId) to (userRole ?: "CUSTOMER")
    }

    private fun unauthorized(): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Missing authenticated user context."))
}
