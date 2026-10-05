package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "SchoolReference")
public class SchoolReference {
    @Id @Column(name = "schoolCode", length = 60) private String schoolCode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "facultyCode", nullable = false) private FacultyReference faculty;
    @Column(name = "schoolName", length = 220, nullable = false) private String schoolName;
    @Column(name = "sourceYear", nullable = false) private short sourceYear;
    @Column(name = "active", nullable = false) private boolean active;
}
