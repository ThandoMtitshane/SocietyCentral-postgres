package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Returned after a successful RSVP confirmation. Contains everything
 * needed by the frontend to display the confirmation page and QR code.
 */
@Data
@Builder
public class RSVPResponseDTO {

    // RSVP info
    private String rsvpReference;
    private String qrCodeTicket;
    private String studentNumber;
    private String studentName;

    // Event info
    private String eventID;
    private String eventName;
    private String eventDescription;
    private LocalDate eventDate;
    private LocalTime eventStartTime;
    private LocalTime eventEndTime;
    private String venueName;
    private String campus;
    private String societyName;
    private String societyID;

    // Capacity info
    private Integer eventLimit;
    private Integer confirmedRSVPs;
    private Integer remainingSpaces;

    // Media
    private String posterUrl;
    private String bannerUrl;
}
