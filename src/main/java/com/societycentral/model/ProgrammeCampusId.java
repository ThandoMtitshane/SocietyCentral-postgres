package com.societycentral.model;

import jakarta.persistence.Embeddable;
import lombok.*;
import java.io.Serializable;

@Embeddable @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ProgrammeCampusId implements Serializable {
    private String programmeCode;
    private String campusCode;
}
