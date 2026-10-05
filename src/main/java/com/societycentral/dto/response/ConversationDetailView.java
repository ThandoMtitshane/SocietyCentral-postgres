package com.societycentral.dto.response;

import com.societycentral.model.ConversationStatus;
import com.societycentral.model.ConversationType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Full conversation view: header metadata, the participant list (for mention
 * autocomplete and the header), and a page of messages (newest first).
 */
@Getter
@Builder
public class ConversationDetailView {

    private String conversationID;
    private ConversationType type;
    private ConversationStatus status;
    private String title;
    private String subtitle;
    private String societyID;

    private String contactReason;
    private boolean incomingRequest;
    /** True when the viewer may currently send messages (ACTIVE + participant). */
    private boolean canSend;

    private List<ConversationParticipantView> participants;

    private List<MessageView> messages;
    private int page;
    private int size;
    private long totalMessages;
    private boolean hasMore;
}
