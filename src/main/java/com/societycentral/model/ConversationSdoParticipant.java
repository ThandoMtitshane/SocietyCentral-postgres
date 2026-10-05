package com.societycentral.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * SDO membership of an institutional society channel. This separate bridge
 * retains the existing student-backed participant model while using the
 * project's real SDO identity and foreign key.
 */
@Entity
@Table(name = "ConversationSDOParticipant")
@Getter
@Setter
@NoArgsConstructor
public class ConversationSdoParticipant {

    @EmbeddedId
    private ConversationSdoParticipantId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("conversationID")
    @JoinColumn(name = "conversationID")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("sdoStaffNumber")
    @JoinColumn(name = "sdoStaffNumber")
    private SDO sdo;

    @Column(name = "role", length = 20, nullable = false)
    private String role;

    @Column(name = "lastReadAt")
    private LocalDateTime lastReadAt;

    @Column(name = "joinedAt", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "leftAt")
    private LocalDateTime leftAt;
}
