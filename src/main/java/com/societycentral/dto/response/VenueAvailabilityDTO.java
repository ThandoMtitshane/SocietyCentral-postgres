package com.societycentral.dto.response;

import com.societycentral.model.Campus;
import lombok.Builder;
import lombok.Value;

/**
 * Read-only venue details returned for an available event time window.
 */
@Value
@Builder
public class VenueAvailabilityDTO {

    String venueCode;
    Campus campus;
    String venueName;
    String venueType;
    Integer capacity;
}
