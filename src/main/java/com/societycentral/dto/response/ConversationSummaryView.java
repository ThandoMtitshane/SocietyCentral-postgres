package com.societycentral.dto.response;

import com.societycentral.model.ConversationStatus;
import com.societycentral.model.ConversationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * One inbox row: a conversation with its display title, last-message preview,
 * unread count, and (for pending cross-society requests) whether the viewer is
 * the recipient who must accept or reject it.
 */
@Getter
@Builder
public class ConversationSummaryView {

    private String conversationID;
    private ConversationType type;
    private ConversationStatus status;

    /** Group name, or the other person's name for a direct chat. */
    private String title;
    /** Role/society context displayed below the conversation title. */
    private String subtitle;
    private String societyID;

    private String lastMessagePreview;
    private String lastMessageSenderName;
    private LocalDateTime lastMessageAt;

    private long unreadCount;

    /** Reason attached to a cross-society request (shown before accepting). */
    private String contactReason;
    /** True when this is a PENDING request awaiting the viewer's decision. */
    private boolean incomingRequest;
}
