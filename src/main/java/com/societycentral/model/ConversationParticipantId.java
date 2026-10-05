package com.societycentral.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for {@link ConversationParticipant}: one row per
 * (conversation, participating student).
 */
@Setter
@Getter
@Embeddable
@AllArgsConstructor
public class ConversationParticipantId implements Serializable {

    private String conversationID;
    private String studentNumber;

    public ConversationParticipantId() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversationParticipantId that)) return false;
        return Objects.equals(conversationID, that.conversationID)
                && Objects.equals(studentNumber, that.studentNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationID, studentNumber);
    }
}
