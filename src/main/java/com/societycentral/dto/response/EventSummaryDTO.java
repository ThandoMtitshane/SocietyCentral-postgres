package com.societycentral.dto.response;

import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Compact event representation used in dashboard cards.
 * Full event details are fetched separately when a user taps "More Details".
 */
@Builder
@Data
@AllArgsConstructor
public class EventSummaryDTO {

    private String eventID;
    private String eventName;
    private LocalDate eventDate;
    private LocalTime eventTime;
    private String eventVenue;
    private String eventCampus;
    private String eventStatus;
    private String imageUrl;
    private String primarySocietyName; // name of the primary hosting society
    private String studentRsvpStatus;
    private Integer eventLimit;
    private String attendingType;

    public EventSummaryDTO() {}

    public EventSummaryDTO(String eventID, String eventName, LocalDate eventDate,
                           LocalTime eventTime, String eventVenue, String eventCampus,
                           String eventStatus, String imageUrl, String primarySocietyName) {
        this(eventID, eventName, eventDate, eventTime, eventVenue, eventCampus,
                eventStatus, imageUrl, primarySocietyName, null,null,null);
    }

    public void setEventLimit(Integer eventLimit) {
        this.eventLimit = eventLimit;
    }

    public void setAttendingType(String attendingType) {
        this.attendingType = attendingType;
    }

}
