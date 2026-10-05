package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "ResidenceReference")
public class ResidenceReference {
    @Id @Column(name = "residenceID") private Integer residenceID;
    @Column(name = "campusCode", length = 40, nullable = false) private String campusCode;
    @Column(name = "residenceName", length = 200, nullable = false) private String residenceName;
    @Column(name = "formerName", length = 200) private String formerName;
    @Column(name = "active", nullable = false) private boolean active;
}
