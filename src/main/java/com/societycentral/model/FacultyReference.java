package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "Faculty")
public class FacultyReference {
    @Id @Column(name = "facultyCode", length = 20) private String facultyCode;
    @Column(name = "facultyName", length = 200, nullable = false, unique = true) private String facultyName;
    @Column(name = "sourceYear", nullable = false) private short sourceYear;
    @Column(name = "active", nullable = false) private boolean active;
}
