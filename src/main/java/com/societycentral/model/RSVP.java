package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "RSVP")
public class RSVP {

    @EmbeddedId
    private RsvpId id;

    @ManyToOne
    @MapsId("eventID")
    @JoinColumn(name = "eventID")
    private Event event;

    @ManyToOne
    @MapsId("studentNumber")
    @JoinColumn(name = "studentNumber")
    private Student student;

    @Column(name = "QRcodeTicket", length = 100, unique = true)
    private String qrCodeTicket;

    @Column(name = "scannedStatus")
    private Boolean scannedStatus = false;

    @Column(name = "scannedAt")
    private LocalDateTime scannedAt;

    @Column(name = "rsvpCreatedAt")
    private LocalDateTime rsvpCreatedAt;

    @Column(name = "checkInMethod", length = 20)
    private String checkInMethod;

    @Column(name = "checkedInBy", length = 100)
    private String checkedInBy;


}
