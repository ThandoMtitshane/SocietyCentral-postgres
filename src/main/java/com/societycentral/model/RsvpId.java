package com.societycentral.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Setter
@Getter
@Embeddable
@AllArgsConstructor
public class RsvpId implements Serializable {

    private String eventID;
    private String studentNumber;

    public RsvpId() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RsvpId)) return false;
        RsvpId that = (RsvpId) o;
        return Objects.equals(eventID, that.eventID)
                && Objects.equals(studentNumber, that.studentNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventID, studentNumber);
    }
}
