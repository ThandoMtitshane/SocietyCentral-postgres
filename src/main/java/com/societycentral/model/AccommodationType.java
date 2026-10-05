package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "AccommodationType")
public class AccommodationType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "accommodationTypeID") private Integer accommodationTypeID;
    @Column(name = "active", nullable = false) private boolean active;
}
