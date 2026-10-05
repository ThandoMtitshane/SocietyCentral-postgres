package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "OffCampusProperty")
public class OffCampusProperty {
    @Id @Column(name = "offCampusPropertyID") private Integer offCampusPropertyID;
    @Column(name = "propertyName", length = 220, nullable = false) private String propertyName;
    @Column(name = "active", nullable = false) private boolean active;
}
