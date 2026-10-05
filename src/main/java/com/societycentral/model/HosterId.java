package com.societycentral.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Data
@AllArgsConstructor
public class HosterId implements Serializable {

    private String eventID;
    private String societyID;

    public HosterId() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HosterId)) return false;
        HosterId that = (HosterId) o;
        return Objects.equals(eventID, that.eventID)
                && Objects.equals(societyID, that.societyID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventID, societyID);
    }
}
