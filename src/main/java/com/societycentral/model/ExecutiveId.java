package com.societycentral.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Embeddable
@AllArgsConstructor
@Data
public class ExecutiveId implements Serializable {

    private String studentNumber;
    private String societyID;
    private LocalDate termStartDate;

    public ExecutiveId() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExecutiveId)) return false;
        ExecutiveId that = (ExecutiveId) o;
        return Objects.equals(studentNumber, that.studentNumber)
                && Objects.equals(societyID, that.societyID)
                && Objects.equals(termStartDate, that.termStartDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(studentNumber, societyID, termStartDate);
    }
}
