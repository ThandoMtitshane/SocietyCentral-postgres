package com.societycentral.controller;

import com.societycentral.dto.request.CreateEventRequestDTO;
import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.EventResponseDTO;
import com.societycentral.dto.response.ExecutiveEventPageDTO;
import com.societycentral.model.EventStatus;
import com.societycentral.service.EventService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller responsible for event operations performed by society
 * executives.
 *
 * C100 and C200 use this controller to allow authenticated executives to
 * create, update and delete event drafts, submit them for approval and
 * withdraw proposals that are still awaiting SDO review.
 *
 */
@RestController
@RequestMapping("/api/executive")
public class ExecutiveEventController {

    private final EventService eventService;

    /**
     * Creates the controller with the event service required to process
     * executive event operations.
     *
     * @param eventService service responsible for event business logic
     */
    @Autowired
    public ExecutiveEventController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Returns a page of events hosted by the authenticated executive's active
     * society.
     *
     * <p>The society identifier is resolved from the authenticated executive
     * record and is never accepted from the browser.</p>
     *
     * @param status optional workflow-status filter
     * @param page zero-based page number
     * @param size requested page size
     * @param search optional event-name or venue-name search text
     * @param userDetails authenticated user supplied by Spring Security
     * @return society-scoped event page in the standard API envelope
     */
    @GetMapping("/events")
    public ResponseEntity<ApiResponse<ExecutiveEventPageDTO>> getEvents(
            @RequestParam(required = false) EventStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal UserDetails userDetails) {

        ExecutiveEventPageDTO events = eventService.getExecutiveEvents(
                userDetails.getUsername(),
                status,
                page,
                size,
                search);

        return ResponseEntity.ok(ApiResponse.success(
                "Events retrieved successfully.",
                events));
    }

    /**
     * Returns complete preview/edit details for one society-owned event.
     *
     * @param eventID event identifier
     * @param userDetails authenticated user supplied by Spring Security
     * @return complete event details
     */
    @GetMapping("/events/{eventID}")
    public ResponseEntity<ApiResponse<EventResponseDTO>> getEvent(
            @PathVariable String eventID,
            @AuthenticationPrincipal UserDetails userDetails) {

        EventResponseDTO event = eventService.getExecutiveEvent(
                eventID,
                userDetails.getUsername());

        return ResponseEntity.ok(ApiResponse.success(
                "Event retrieved successfully.",
                event));
    }

    /**
     * Creates a new event proposal for a society.
     *
     * The authenticated user's email address is obtained from the security
     * context and passed to the service layer. The service verifies that the
     * user is an active executive of the society before creating the event.
     *
     * Newly created events are stored with DRAFT status and linked to the
     * society through a primary Hoster record.
     *
     * Endpoint:
     * POST /api/executive/societies/{societyID}/events
     *
     * @param societyID identifier of the society creating the event
     * @param request event details submitted by the executive
     * @param userDetails authenticated user details supplied by Spring Security
     * @return the created event wrapped in the standard API response
     */
    @PostMapping("/societies/{societyID}/events")
    public ResponseEntity<ApiResponse<EventResponseDTO>> createEvent(
            @PathVariable String societyID,
            @Valid @RequestBody CreateEventRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // The JWT authentication filter stores the authenticated user's email
        // as the Spring Security username.
        String executiveEmail = userDetails.getUsername();

        EventResponseDTO createdEvent = eventService.createEvent(
                societyID,
                executiveEmail,
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                        "Event draft saved successfully.",
                        createdEvent
                ));
    }

    /**
     * Updates the existing Event row for a DRAFT or REJECTED event.
     *
     * <p>A rejected event moves back to DRAFT after a successful save while
     * its rejection feedback remains stored for reference. Submission remains
     * a separate explicit action.</p>
     *
     * @param eventID event identifier
     * @param request complete editable event form
     * @param userDetails authenticated user supplied by Spring Security
     * @return the updated event
     */
    @PutMapping("/events/{eventID}")
    public ResponseEntity<ApiResponse<EventResponseDTO>> updateEvent(
            @PathVariable String eventID,
            @Valid @RequestBody CreateEventRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        EventResponseDTO event = eventService.updateExecutiveEvent(
                eventID,
                userDetails.getUsername(),
                request);

        return ResponseEntity.ok(ApiResponse.success(
                "Event draft updated successfully.",
                event));
    }

    /**
     * Permanently deletes an event that is still in DRAFT status.
     *
     * <p>The authenticated executive email is supplied by Spring Security.
     * Society ownership is resolved by the service and is not accepted from
     * the request.</p>
     *
     * Endpoint:
     * DELETE /api/executive/events/{eventID}
     *
     * @param eventID identifier of the draft event
     * @param userDetails authenticated user supplied by Spring Security
     * @return standard success response with no data payload
     */
    @DeleteMapping("/events/{eventID}")
    public ResponseEntity<ApiResponse<Void>> deleteDraftEvent(
            @PathVariable String eventID,
            @AuthenticationPrincipal UserDetails userDetails) {

        eventService.deleteDraftEvent(
                eventID,
                userDetails.getUsername()
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Draft deleted successfully.",
                        null
                )
        );
    }

    /**
     * Event submission is now handled by EventProposalController
     * which includes budget validation, co-host invitations, and SDO email.
     */

    /**
     * Withdraws a proposed event while it is awaiting SDO review.
     *
     * <p>The authenticated executive email is read from Spring Security. The
     * service resolves society ownership from persistence, so no society
     * identifier or executive email is accepted from the request body.</p>
     *
     * Endpoint:
     * POST /api/executive/events/{eventID}/withdraw
     *
     * @param eventID identifier of the proposed event
     * @param userDetails authenticated user details supplied by Spring Security
     * @return the withdrawn event wrapped in the standard API response
     */
    @PostMapping("/events/{eventID}/withdraw")
    public ResponseEntity<ApiResponse<EventResponseDTO>> withdrawEventProposal(
            @PathVariable String eventID,
            @AuthenticationPrincipal UserDetails userDetails) {

        EventResponseDTO withdrawnEvent =
                eventService.withdrawEventProposal(
                        eventID,
                        userDetails.getUsername()
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Event proposal withdrawn successfully.",
                        withdrawnEvent
                )
        );
    }
}
