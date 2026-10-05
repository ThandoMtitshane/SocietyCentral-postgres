package com.societycentral.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Lightweight published-event card returned by the A400 browse endpoint.
 */
@Data
public class StudentEventSummaryDTO {
    private String eventID;
    private String eventName;
    private String eventDescription;

    private LocalDate eventDate;
    private LocalTime eventStartTime;
    private LocalTime eventEndTime;

    private String venueName;
    private String campus;

    private String eventCategory;
    private String attendingType;

    private String primarySocietyID;
    private String primarySocietyName;
    private String posterUrl;
    private String bannerUrl;

    private Integer eventLimit;
    private Integer confirmedRSVPs;
    private Integer remainingSpaces;

    private LocalDateTime rsvpOpenDate;
    private LocalDateTime rsvpCloseDate;

    private String rsvpAvailability;
    private String studentRsvpStatus;
}
