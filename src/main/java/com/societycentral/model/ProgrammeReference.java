package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "ProgrammeReference")
public class ProgrammeReference {
    @Id @Column(name = "programmeCode", length = 120) private String programmeCode;
    @Column(name = "programmeName", length = 300, nullable = false, unique = true) private String programmeName;
    @Column(name = "qualificationLevel", length = 50, nullable = false) private String qualificationLevel;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "facultyCode", nullable = false) private FacultyReference faculty;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "schoolCode") private SchoolReference school;
    @Column(name = "sourceYear", nullable = false) private short sourceYear;
    @Column(name = "active", nullable = false) private boolean active;
}
