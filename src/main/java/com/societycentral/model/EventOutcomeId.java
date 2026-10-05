package com.societycentral.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@Embeddable
@AllArgsConstructor
public class EventOutcomeId implements Serializable {

    private String eventID;
    private String societyID;

    public EventOutcomeId() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventOutcomeId)) return false;
        EventOutcomeId that = (EventOutcomeId) o;
        return Objects.equals(eventID, that.eventID)
                && Objects.equals(societyID, that.societyID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventID, societyID);
    }
}
