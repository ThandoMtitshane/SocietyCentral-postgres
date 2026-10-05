package com.societycentral.dto.response;

import com.societycentral.model.MessageStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One message as shown in a conversation, including sender identity, an
 * optional quoted reply, structured mentions, and whether the viewer may still
 * edit/delete it (within the 15-minute window and only their own message).
 */
@Getter
@Builder
public class MessageView {

    private String messageID;
    private String conversationID;

    private String senderStudentNumber;
    private String senderSdoStaffNumber;
    private String senderEmail;
    /** EXECUTIVE or SDO. */
    private String senderType;
    private String senderName;
    private String senderContext;

    private String body;
    private MessageStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime editedAt;
    private boolean edited;

    /** Present when this message replies to another (may be a short preview). */
    private String replyToMessageID;
    private String replyToSenderName;
    private String replyToPreview;

    private List<MentionView> mentions;

    /** True when the viewer sent this message. */
    private boolean mine;
    /** True when the viewer may still edit this message (own + within 15 min). */
    private boolean canEdit;
    /** True when the viewer may still delete this message (own + within 15 min). */
    private boolean canDelete;
}
