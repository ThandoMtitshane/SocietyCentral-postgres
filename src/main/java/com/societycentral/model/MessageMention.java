package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A structured @mention inside a {@link Message}. Either a {@code USER} mention
 * (an executive, {@code targetStudentNumber} set) or an {@code EVENT} mention
 * (an event hosted by the sender's own society, {@code targetEventID} set).
 * Storing mentions structurally lets the frontend render reliable links
 * without re-parsing the message body.
 */
@Entity
@Table(name = "MessageMention")
@Getter
@Setter
@NoArgsConstructor
public class MessageMention {

    @Id
    @Column(name = "mentionID", length = 36)
    private String mentionID;

    @Column(name = "messageID", length = 36, nullable = false)
    private String messageID;

    @Enumerated(EnumType.STRING)
    @Column(name = "mentionType", length = 20, nullable = false)
    private MentionType mentionType;

    /** Set when {@code mentionType == USER}. */
    @Column(name = "targetStudentNumber", length = 20)
    private String targetStudentNumber;

    /** Set when {@code mentionType == EVENT}. */
    @Column(name = "targetEventID", length = 20)
    private String targetEventID;
}
