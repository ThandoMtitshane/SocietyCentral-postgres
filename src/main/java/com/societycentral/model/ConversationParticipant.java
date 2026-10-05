package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Membership of one executive in one {@link Conversation}. A participant whose
 * executive term has ended has {@code leftAt} set and no longer receives
 * messages. {@code lastReadAt} drives unread badges.
 */
@Getter
@Setter
@Entity
@Table(name = "ConversationParticipant")
@NoArgsConstructor
public class ConversationParticipant {

    @EmbeddedId
    private ConversationParticipantId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("conversationID")
    @JoinColumn(name = "conversationID")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("studentNumber")
    @JoinColumn(name = "studentNumber")
    private Student student;

    @Column(name = "role", length = 20, nullable = false)
    private String role;

    @Column(name = "lastReadAt")
    private LocalDateTime lastReadAt;

    @Column(name = "joinedAt", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "leftAt")
    private LocalDateTime leftAt;
}
