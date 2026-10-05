package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "ProgrammeCampus")
public class ProgrammeCampus {
    @EmbeddedId private ProgrammeCampusId id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("programmeCode") @JoinColumn(name = "programmeCode", nullable = false) private ProgrammeReference programme;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @MapsId("campusCode") @JoinColumn(name = "campusCode", nullable = false) private CampusReference campus;
    @Column(name = "sourceYear", nullable = false) private short sourceYear;
    @Column(name = "evidence", length = 200, nullable = false) private String evidence;
}
