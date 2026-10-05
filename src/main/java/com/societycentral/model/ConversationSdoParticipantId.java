package com.societycentral.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for an SDO participant in a conversation. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSdoParticipantId implements Serializable {

    @Column(name = "conversationID", length = 36)
    private String conversationID;

    @Column(name = "sdoStaffNumber", length = 20)
    private String sdoStaffNumber;

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ConversationSdoParticipantId that)) return false;
        return Objects.equals(conversationID, that.conversationID)
                && Objects.equals(sdoStaffNumber, that.sdoStaffNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationID, sdoStaffNumber);
    }
}
