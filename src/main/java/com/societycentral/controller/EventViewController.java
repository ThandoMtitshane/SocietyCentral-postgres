package com.societycentral.controller;

import com.societycentral.dto.response.EventSummaryDTO;
import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.EventResponseDTO;
import com.societycentral.dto.response.StudentEventSummaryDTO;
import com.societycentral.service.EventViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A400 — View Events
 *
 * GET /api/events
 * GET /api/events/visible (legacy alias)
 *
 * Returns published events the authenticated user is eligible to see.
 * Student: member societies + EVERY_STUDENT events.
 * Active executive and SDO: all published events.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventViewController {

    private final EventViewService eventViewService;

    /**
     * Returns all published events visible to the authenticated user.
     *
     * Students receive:
     * - Events hosted by societies they belong to.
     * - Events open to every student.
     *
     * Active executives and Student Development Officers receive:
     * - Every published event.
     *
     * @param authentication authenticated user
     * @return visible events
     */
    @GetMapping({"", "/visible"})
    public ResponseEntity<ApiResponse<List<StudentEventSummaryDTO>>> getVisibleEvents(
            Authentication authentication) {

        // 1. Resolve the authenticated email
        String email = authentication.getName();

        // 2. Determine whether the user is an SDO
        boolean isSDO = hasSDORole(authentication);

        // 3. Retrieve events through the matching visibility rules. Active
        // executive status is resolved from persistence because executives
        // authenticate with ROLE_STUDENT.
        List<StudentEventSummaryDTO> events;

        if (isSDO) {
            events = eventViewService.getAllPublishedEvents();
        } else {
            events = eventViewService.getVisibleEventsForStudent(email);
        }

        // 4. Return the standard API response
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Events retrieved successfully",
                        events
                )
        );
    }

    /**
     * B800 — Request Budget
     *
     * Returns the events that the authenticated Executive
     * may request a budget for (events hosted by their own society).
     */
    @GetMapping("/eligible-for-budget")
    public ResponseEntity<ApiResponse<List<EventSummaryDTO>>> getEligibleBudgetEvents(
            Authentication authentication) {

        List<EventSummaryDTO> events =
                eventViewService.getEligibleBudgetEvents(authentication.getName());

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Eligible events retrieved successfully.",
                        events
                )
        );
    }

    /**
     * Returns complete details for one published event visible to the
     * authenticated user.
     *
     * <p>Students remain active-membership scoped while active executives and
     * SDOs may retrieve any published event.</p>
     *
     * @param eventID requested event identifier
     * @param authentication authenticated user
     * @return complete visible event details
     */
    @GetMapping("/{eventID}")
    public ResponseEntity<ApiResponse<EventResponseDTO>> getVisibleEvent(
            @PathVariable String eventID,
            Authentication authentication) {

        // 1. Resolve the authenticated email
        String email = authentication.getName();

        // 2. Determine whether the user is an SDO
        boolean isSDO = hasSDORole(authentication);

        // 3. Retrieve the event through the matching visibility rules. Active
        // executive status is resolved by the service from the database.
        EventResponseDTO event;

        if (isSDO) {
            event = eventViewService.getPublishedEvent(eventID);
        } else {
            event = eventViewService.getVisibleEventForStudent(
                    eventID,
                    email
            );
        }

        // 4. Return the standard API response
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Event retrieved successfully",
                        event
                )
        );
    }

    /**
     * Checks whether the authenticated user has the SDO authority used by
     * both A400 endpoints.
     *
     * @param authentication authenticated user
     * @return {@code true} when ROLE_SDO is present
     */
    private boolean hasSDORole(
            Authentication authentication) {

        return hasRole(authentication, "ROLE_SDO");
    }

    /**
     * Checks whether an authority is present on the authenticated user.
     *
     * @param authentication authenticated user
     * @param role required authority
     * @return {@code true} when the authority is present
     */
    private boolean hasRole(
            Authentication authentication,
            String role) {

        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals(role));
    }
}
