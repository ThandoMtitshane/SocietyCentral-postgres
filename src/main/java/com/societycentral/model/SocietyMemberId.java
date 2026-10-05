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
public class SocietyMemberId implements Serializable {

    private String studentNumber;
    private String societyID;

    public SocietyMemberId() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SocietyMemberId)) return false;
        SocietyMemberId that = (SocietyMemberId) o;
        return Objects.equals(studentNumber, that.studentNumber)
                && Objects.equals(societyID, that.societyID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(studentNumber, societyID);
    }
}
