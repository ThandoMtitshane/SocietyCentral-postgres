package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "POAEventCoHost")
@Getter
@Setter
@NoArgsConstructor
public class POAEventCoHost {

    @Id
    @Column(name = "coHostID", length = 36)
    private String coHostID;

    @Column(name = "poaEventID", length = 36, nullable = false)
    private String poaEventID;

    @Column(name = "invitedSocietyID", length = 20, nullable = false)
    private String invitedSocietyID;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private POACoHostStatus status = POACoHostStatus.PENDING;

    @Column(name = "respondedAt")
    private LocalDateTime respondedAt;

    public static String newID() {
        return UUID.randomUUID().toString();
    }
}