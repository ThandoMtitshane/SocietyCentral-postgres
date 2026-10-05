package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @IdClass(OffCampusAccreditationId.class) @Table(name = "OffCampusAccreditation")
public class OffCampusAccreditation {
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "offCampusPropertyID", nullable = false) private OffCampusProperty property;
    @Id @Column(name = "academicYear", nullable = false) private Short academicYear;
    @Column(name = "accredited", nullable = false) private boolean accredited;
}
