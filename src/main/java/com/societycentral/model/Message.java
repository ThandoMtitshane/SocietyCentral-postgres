package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A single chat message inside a {@link Conversation}. May reply to another
 * message ({@code replyToMessageID}). Editing and deleting-for-everyone are
 * only permitted within 15 minutes of sending (enforced in the service layer).
 */
@Entity
@Table(name = "Message")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @Column(name = "messageID", length = 36)
    private String messageID;

    @Column(name = "conversationID", length = 36, nullable = false)
    private String conversationID;

    /** Set for messages sent by an executive. */
    @Column(name = "senderStudentNumber", length = 20)
    private String senderStudentNumber;

    /** Set for messages sent by an SDO. Exactly one sender column is set. */
    @Column(name = "senderSdoStaffNumber", length = 20)
    private String senderSdoStaffNumber;

    @Column(name = "body", length = 2000, nullable = false)
    private String body;

    /** Message this one replies to, within the same conversation; nullable. */
    @Column(name = "replyToMessageID", length = 36)
    private String replyToMessageID;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private MessageStatus status;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "editedAt")
    private LocalDateTime editedAt;
}
