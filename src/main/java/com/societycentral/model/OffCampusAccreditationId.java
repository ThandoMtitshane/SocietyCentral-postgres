package com.societycentral.model;

import java.io.Serializable;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class OffCampusAccreditationId implements Serializable {
    private Integer property;
    private Short academicYear;
}
