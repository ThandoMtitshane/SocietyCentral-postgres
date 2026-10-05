package com.societycentral.controller;

import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.VenueAvailabilityDTO;
import com.societycentral.model.Campus;
import com.societycentral.service.VenueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Exposes read-only venue selection operations to authenticated executives.
 */
@RestController
@RequestMapping("/api/executive/venues")
public class ExecutiveVenueController {

    private final VenueService venueService;

    /**
     * Creates the executive venue controller.
     *
     * @param venueService venue availability service
     */
    @Autowired
    public ExecutiveVenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    /**
     * Returns active venues without a blocking event in the requested window.
     *
     * @param campus selected campus
     * @param eventDate selected event date
     * @param startTime selected start time
     * @param endTime selected end time
     * @param excludeEventID optional event currently being edited
     * @param eventID compatibility alias used by the current edit client
     * @return standard response containing available venues
     */
    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<VenueAvailabilityDTO>>> getAvailableVenues(
            @RequestParam(required = false) Campus campus,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate eventDate,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "HH:mm")
            LocalTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "HH:mm")
            LocalTime endTime,
            @RequestParam(required = false) String excludeEventID,
            @RequestParam(required = false) String eventID) {
        // BUSINESS RULE: VenueService validates nullable query parameters in
        // the documented order so clients receive stable ApiResponse messages
        // instead of framework-level missing-parameter errors.
        // excludeEventID is the canonical API name. eventID remains accepted
        // so deployed edit clients do not silently lose self-exclusion.
        String effectiveExcludeEventID =
                excludeEventID == null || excludeEventID.isBlank()
                        ? eventID
                        : excludeEventID;
        List<VenueAvailabilityDTO> venues = venueService.getAvailableVenues(
                campus,
                eventDate,
                startTime,
                endTime,
                effectiveExcludeEventID);

        return ResponseEntity.ok(
                ApiResponse.success("Available venues loaded.", venues));
    }
}
