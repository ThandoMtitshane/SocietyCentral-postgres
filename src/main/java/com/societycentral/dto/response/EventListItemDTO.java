package com.societycentral.dto.response;

import com.societycentral.model.Campus;
import com.societycentral.model.EventStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Compact event representation used by the executive event-management list.
 *
 * <p>Venue and society values are resolved by the backend from authoritative
 * persistence relationships. The DTO never echoes client-supplied ownership
 * or capacity data.</p>
 */
@Data
@Builder
public class EventListItemDTO {

    private String eventID;
    private String eventName;
    private String eventDescriptionSummary;
    private LocalDate eventDate;
    private LocalTime eventStartTime;
    private LocalTime eventEndTime;
    private EventStatus eventStatus;
    private Campus campus;
    private String venueCode;
    private String venueName;
    private Integer venueCapacity;
    private Integer eventLimit;
    private String posterUrl;
    private String bannerUrl;
    private String societyID;
    private String societyName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String rejectionReason;
}
