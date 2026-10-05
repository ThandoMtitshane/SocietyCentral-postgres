package com.societycentral.controller;

import com.societycentral.dto.request.EditMessageRequestDTO;
import com.societycentral.dto.request.SendMessageRequestDTO;
import com.societycentral.dto.request.StartDirectConversationRequestDTO;
import com.societycentral.dto.response.*;
import com.societycentral.service.MessagingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for the shared Executive and SDO messaging domain.
 *
 * <p>The service resolves the authenticated email to an active executive or
 * SDO and applies conversation-specific authorization. The legacy executive
 * route is preserved while the SDO alias exposes the same API.</p>
 */
@RestController
@RequestMapping({"/api/executive/messaging", "/api/sdo/messaging"})
@PreAuthorize("hasAnyRole('STUDENT','SDO')")
@RequiredArgsConstructor
public class MessagingController {

    private final MessagingService messagingService;

    /** Inbox: society groups + direct chats + pending requests. */
    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<ConversationSummaryView>>>
    getInbox(Authentication authentication) {
        List<ConversationSummaryView> inbox =
                messagingService.getInbox(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "Conversations retrieved successfully.", inbox));
    }

    /** One conversation's header, participants, and a page of messages. */
    @GetMapping("/conversations/{conversationID}")
    public ResponseEntity<ApiResponse<ConversationDetailView>>
    getConversation(
            @PathVariable String conversationID,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            Authentication authentication) {
        ConversationDetailView detail = messagingService.getConversation(
                authentication.getName(), conversationID, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                "Conversation loaded successfully.", detail));
    }

    /** Sends a message into an existing conversation. */
    @PostMapping("/conversations/{conversationID}/messages")
    public ResponseEntity<ApiResponse<MessageView>> sendMessage(
            @PathVariable String conversationID,
            @Valid @RequestBody SendMessageRequestDTO request,
            Authentication authentication) {
        MessageView message = messagingService.sendMessage(
                authentication.getName(), conversationID, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message sent.", message));
    }

    /** Edits the caller's own message (within 15 minutes of sending). */
    @PutMapping("/conversations/{conversationID}/messages/{messageID}")
    public ResponseEntity<ApiResponse<MessageView>> editMessage(
            @PathVariable String conversationID,
            @PathVariable String messageID,
            @Valid @RequestBody EditMessageRequestDTO request,
            Authentication authentication) {
        MessageView message = messagingService.editMessage(
                authentication.getName(), conversationID, messageID, request);
        return ResponseEntity.ok(ApiResponse.success(
                "Message updated.", message));
    }

    /** Deletes the caller's own message for everyone (within 15 minutes). */
    @DeleteMapping("/conversations/{conversationID}/messages/{messageID}")
    public ResponseEntity<ApiResponse<MessageView>> deleteMessage(
            @PathVariable String conversationID,
            @PathVariable String messageID,
            Authentication authentication) {
        MessageView message = messagingService.deleteMessage(
                authentication.getName(), conversationID, messageID);
        return ResponseEntity.ok(ApiResponse.success(
                "Message deleted.", message));
    }

    /**
     * Starts a direct conversation with its opening message, or sends the
     * supplied body normally when an ACTIVE direct conversation already exists.
     */
    @PostMapping("/conversations/direct")
    public ResponseEntity<ApiResponse<ConversationDetailView>>
    startDirectConversation(
            @Valid @RequestBody StartDirectConversationRequestDTO request,
            Authentication authentication) {
        ConversationDetailView detail =
                messagingService.startDirectConversation(
                        authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Conversation started.", detail));
    }

    /**
     * Starts or continues the assigned SDO's institutional conversation with
     * the society's current President and Secretary.
     */
    @PostMapping("/conversations/sdo-society/{societyID}")
    @PreAuthorize("hasRole('SDO')")
    public ResponseEntity<ApiResponse<ConversationDetailView>>
    startSdoSocietyConversation(
            @PathVariable String societyID,
            @Valid @RequestBody SendMessageRequestDTO request,
            Authentication authentication) {
        ConversationDetailView detail =
                messagingService.startSdoSocietyConversation(
                        authentication.getName(), societyID, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Leadership message sent.", detail));
    }

    /** Accepts a pending cross-society message request. */
    @PostMapping("/conversations/{conversationID}/accept")
    public ResponseEntity<ApiResponse<ConversationDetailView>>
    acceptRequest(
            @PathVariable String conversationID,
            Authentication authentication) {
        ConversationDetailView detail = messagingService.respondToRequest(
                authentication.getName(), conversationID, true);
        return ResponseEntity.ok(ApiResponse.success(
                "Request accepted.", detail));
    }

    /** Rejects a pending cross-society message request. */
    @PostMapping("/conversations/{conversationID}/reject")
    public ResponseEntity<ApiResponse<ConversationDetailView>>
    rejectRequest(
            @PathVariable String conversationID,
            Authentication authentication) {
        ConversationDetailView detail = messagingService.respondToRequest(
                authentication.getName(), conversationID, false);
        return ResponseEntity.ok(ApiResponse.success(
                "Request declined.", detail));
    }

    /** Marks a conversation as read for the caller. */
    @PostMapping("/conversations/{conversationID}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable String conversationID,
            Authentication authentication) {
        messagingService.markRead(authentication.getName(), conversationID);
        return ResponseEntity.ok(ApiResponse.success(
                "Conversation marked as read.", null));
    }

    /** Directory search across current executives by name, role, or society. */
    @GetMapping("/directory")
    public ResponseEntity<ApiResponse<List<DirectoryEntryView>>>
    searchDirectory(
            @RequestParam(required = false) String q,
            Authentication authentication) {
        List<DirectoryEntryView> results =
                messagingService.searchDirectory(authentication.getName(), q);
        return ResponseEntity.ok(ApiResponse.success(
                "Directory retrieved successfully.", results));
    }

    /** Events the caller may @mention (their own society's events). */
    @GetMapping("/mentionable-events")
    public ResponseEntity<ApiResponse<List<MentionableEventView>>>
    getMentionableEvents(
            @RequestParam(required = false) String q,
            Authentication authentication) {
        List<MentionableEventView> events =
                messagingService.getMentionableEvents(authentication.getName(), q);
        return ResponseEntity.ok(ApiResponse.success(
                "Events retrieved successfully.", events));
    }
}
