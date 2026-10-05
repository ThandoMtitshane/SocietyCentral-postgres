package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "Hoster")
@Data
public class Hoster {

    @EmbeddedId
    private HosterId id;

    @ManyToOne
    @MapsId("eventID")
    @JoinColumn(name = "eventID")
    private Event event;

    @ManyToOne
    @MapsId("societyID")
    @JoinColumn(name = "societyID")
    private Society society;

    @Column(name = "isPrimary")
    private Boolean isPrimary = false;

}
