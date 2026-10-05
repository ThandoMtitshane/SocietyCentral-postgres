package com.societycentral.mapper;

import com.societycentral.dto.response.EventResponseDTO;
import com.societycentral.model.Campus;
import com.societycentral.model.Event;
import com.societycentral.model.Society;
import com.societycentral.model.Venue;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * Maps event domain records to complete event response payloads.
 */
@Component
public class EventMapper {

    /**
     * Maps an event, its primary society and authoritative venue to the
     * complete response used by event-detail workflows.
     *
     * @param event event being returned
     * @param primarySociety primary hosting society
     * @param venue authoritative venue, or {@code null} for a legacy event
     * @return complete event response
     */
    public EventResponseDTO mapToResponse(
            Event event,
            Society primarySociety,
            Venue venue) {

        LocalTime eventStartTime =
                event.getEventStartTime() != null
                        ? event.getEventStartTime()
                        : event.getEventTime();

        String venueName =
                venue != null
                        ? venue.getVenueName()
                        : event.getEventVenue();

        Campus campus =
                venue != null
                        ? venue.getCampus()
                        : event.getEventCampus();

        return EventResponseDTO.builder()
                .eventID(event.getEventID())
                .eventName(event.getEventName())
                .eventDate(event.getEventDate())
                .eventStartTime(eventStartTime)
                .eventEndTime(event.getEventEndTime())
                .eventTime(eventStartTime)
                .venueCode(event.getVenueCode())
                .venueName(venueName)
                .venueType(
                        venue != null
                                ? venue.getVenueType()
                                : null
                )
                .venueCapacity(
                        venue != null
                                ? venue.getCapacity()
                                : null
                )
                .eventVenue(venueName)
                .eventCampus(campus)
                .campus(campus)
                .eventDescription(event.getEventDescription())
                .advertisementVersion(event.getAdvertisementVersion())
                .eventStatus(event.getEventStatus())
                .rsvpOpenDate(event.getRsvpOpenDate())
                .rsvpCloseDate(event.getRsvpCloseDate())
                .eventLimit(event.getEventLimit())
                .attendingType(event.getAttendingType())
                .overallRating(event.getOverallRating())
                .imageUrl(event.getImageUrl())
                .posterUrl(event.getPosterUrl())
                .bannerUrl(event.getBannerUrl())
                .primarySocietyID(primarySociety.getSocietyID())
                .primarySocietyName(primarySociety.getSocietyName())
                .societyID(primarySociety.getSocietyID())
                .societyName(primarySociety.getSocietyName())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .rejectionReason(event.getRejectionReason())
                .build();
    }
}
