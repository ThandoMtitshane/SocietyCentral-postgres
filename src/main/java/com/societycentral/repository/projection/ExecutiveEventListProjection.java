package com.societycentral.repository.projection;

import com.societycentral.model.Campus;
import com.societycentral.model.EventStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Database projection for the executive event list.
 *
 * <p>The projection is populated by one Hoster/Event/Society/Venue query so
 * list mapping does not load each event or venue separately.</p>
 */
public interface ExecutiveEventListProjection {

    String getEventID();

    String getEventName();

    String getEventDescription();

    LocalDate getEventDate();

    LocalTime getEventStartTime();

    LocalTime getEventEndTime();

    EventStatus getEventStatus();

    Campus getCampus();

    String getVenueCode();

    String getVenueName();

    Integer getVenueCapacity();

    Integer getEventLimit();

    String getPosterUrl();

    String getBannerUrl();

    String getSocietyID();

    String getSocietyName();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();

    String getRejectionReason();
}
