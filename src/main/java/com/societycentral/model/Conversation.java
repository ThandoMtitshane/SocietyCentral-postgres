package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An institutional messaging conversation. Executive groups and SDO society
 * channels are tied to a society; direct conversations join two executives.
 */
@Entity
@Table(name = "Conversation")
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

    @Id
    @Column(name = "conversationID", length = 36)
    private String conversationID;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private ConversationType type;

    /** Owning society for group/SDO channels; null for a direct chat. */
    @Column(name = "societyID", length = 20)
    private String societyID;

    /** Group name; null for direct chats (the title is derived per-viewer). */
    @Column(name = "title", length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ConversationStatus status;

    /** Reason supplied when a cross-society direct request is opened. */
    @Column(name = "contactReason", length = 500)
    private String contactReason;

    /** Student number of the executive who started a direct chat/request. */
    @Column(name = "initiatedByStudentNumber", length = 20)
    private String initiatedByStudentNumber;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    /** Timestamp of the most recent message; drives inbox ordering. */
    @Column(name = "lastMessageAt")
    private LocalDateTime lastMessageAt;
}
