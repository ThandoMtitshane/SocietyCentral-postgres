package com.societycentral.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents an authoritative, bookable university venue.
 *
 * Venue names, types, campuses and capacities are maintained in the database
 * and are never taken from an event-creation request.
 */
@Entity
@Table(name = "Venue")
@Getter
@Setter
@NoArgsConstructor
public class Venue {

    @Id
    @Column(name = "venueCode", length = 10)
    private String venueCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "campus", length = 50, nullable = false)
    private Campus campus;

    @Column(name = "venueName", length = 150, nullable = false)
    private String venueName;

    @Column(name = "venueType", length = 100, nullable = false)
    private String venueType;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
