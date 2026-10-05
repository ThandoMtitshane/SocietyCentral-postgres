package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "CampusReference")
public class CampusReference {
    @Id @Column(name = "campusCode", length = 40) private String campusCode;
    @Column(name = "campusName", length = 120, nullable = false, unique = true) private String campusName;
    @Column(name = "active", nullable = false) private boolean active;
    @Column(name = "city", length = 120) private String city;
    @Column(name = "hasOnCampusResidence", nullable = false) private boolean hasOnCampusResidence;
}
