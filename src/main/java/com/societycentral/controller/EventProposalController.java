package com.societycentral.controller;

import com.societycentral.dto.request.PublishEventRequestDTO;
import com.societycentral.dto.request.ReviewEventRequestDTO;
import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.EventProposalResponseDTO;
import com.societycentral.dto.response.POAEventOptionDTO;
import com.societycentral.service.EventProposalPdfService;
import com.societycentral.service.EventProposalService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Event Proposal endpoints for the revised event workflow.
 *
 * Executive endpoints:
 * - POST /api/executive/events/{eventID}/submit   (submit proposal)
 * - POST /api/executive/events/{eventID}/publish  (publish after approval)
 * - GET  /api/executive/events/poa-options        (POA event dropdown)
 * - GET  /api/executive/events/{eventID}/proposal (view own proposal)
 *
 * SDO endpoints:
 * - POST /api/sdo/events/{eventID}/review         (approve/reject)
 * - GET  /api/sdo/events/{eventID}/proposal       (view proposal for review)
 * - GET  /api/sdo/events/{eventID}/proposal/pdf   (download proposal PDF)
 *
 * Co-host endpoints:
 * - POST /api/executive/events/cohost/{invitationID}/respond (accept/decline)
 */
@RestController
public class EventProposalController {

    private final EventProposalService proposalService;
    private final EventProposalPdfService pdfService;

    @Autowired
    public EventProposalController(EventProposalService proposalService,
                                    EventProposalPdfService pdfService) {
        this.proposalService = proposalService;
        this.pdfService = pdfService;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXECUTIVE ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Submit a DRAFT event for SDO approval. */
    @PostMapping("/api/executive/events/{eventID}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<EventProposalResponseDTO>> submitProposal(
            @PathVariable String eventID,
            Authentication authentication) {

        EventProposalResponseDTO response = proposalService.submitProposal(
                eventID, authentication.getName());

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(
                        "Event proposal submitted for SDO review.", response));
    }

    /** Publish an APPROVED event (adds RSVP config, sends member emails). */
    @PostMapping("/api/executive/events/{eventID}/publish")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<EventProposalResponseDTO>> publishEvent(
            @PathVariable String eventID,
            @Valid @RequestBody PublishEventRequestDTO request,
            Authentication authentication) {

        EventProposalResponseDTO response = proposalService.publishEvent(
                eventID, authentication.getName(), request);

        return ResponseEntity.ok(ApiResponse.success(
                "Event published successfully.", response));
    }

    /** Get POA events for the "Create from POA" dropdown. */
    @GetMapping("/api/executive/events/poa-options")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<POAEventOptionDTO>>> getPOAOptions(
            Authentication authentication) {

        List<POAEventOptionDTO> options = proposalService.getPOAEventsForPrefill(
                authentication.getName());

        return ResponseEntity.ok(ApiResponse.success("POA events loaded.", options));
    }

    /** View own event proposal details. */
    @GetMapping("/api/executive/events/{eventID}/proposal")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<EventProposalResponseDTO>> getExecutiveProposal(
            @PathVariable String eventID, Authentication authentication) {

        EventProposalResponseDTO response = proposalService.getProposal(eventID, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Proposal retrieved.", response));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SDO ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /** SDO reviews (approves or rejects) an event proposal. */
    @PostMapping("/api/sdo/events/{eventID}/review")
    @PreAuthorize("hasRole('SDO')")
    public ResponseEntity<ApiResponse<EventProposalResponseDTO>> reviewProposal(
            @PathVariable String eventID,
            @Valid @RequestBody ReviewEventRequestDTO request,
            Authentication authentication) {

        EventProposalResponseDTO response = proposalService.reviewProposal(
                eventID, authentication.getName(), request);

        String action = request.getAction().toUpperCase().trim();
        String message = "APPROVE".equals(action)
                ? "Event proposal approved."
                : "Event proposal rejected with feedback.";

        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    /** SDO views an event proposal for review. */
    @GetMapping("/api/sdo/events/{eventID}/proposal")
    @PreAuthorize("hasRole('SDO')")
    public ResponseEntity<ApiResponse<EventProposalResponseDTO>> getSDOProposal(
            @PathVariable String eventID,
            Authentication authentication) {

        EventProposalResponseDTO response = proposalService.getProposalForSDO(
                eventID, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Proposal retrieved.", response));
    }

    /** SDO lists all PROPOSED events for their supervised societies. */
    @GetMapping("/api/sdo/events/proposals")
    @PreAuthorize("hasRole('SDO')")
    public ResponseEntity<ApiResponse<List<EventProposalResponseDTO>>> getSDOProposals(
            Authentication authentication) {

        List<EventProposalResponseDTO> proposals = proposalService
                .getProposalsForSDO(authentication.getName());

        return ResponseEntity.ok(ApiResponse.success(
                "Event proposals retrieved.", proposals));
    }

    /** SDO downloads the event proposal as a branded PDF. */
    @GetMapping("/api/sdo/events/{eventID}/proposal/pdf")
    @PreAuthorize("hasRole('SDO')")
    public ResponseEntity<byte[]> downloadProposalPdf(
            @PathVariable String eventID, Authentication authentication) {

        EventProposalResponseDTO proposal = proposalService.getProposalForSDO(
                eventID, authentication.getName());
        byte[] pdfBytes = pdfService.generateProposalPdf(proposal);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment",
                "EventProposal_" + eventID + ".pdf");

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    /** Executive: Get APPROVED + PUBLISHED events for Publish & Advertise page. */
    @GetMapping("/api/executive/events/publishable")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<EventProposalResponseDTO>>> getPublishableEvents(
            Authentication authentication) {

        List<EventProposalResponseDTO> events = proposalService
                .getPublishableEventsForExecutive(authentication.getName());

        return ResponseEntity.ok(ApiResponse.success("Publishable events retrieved.", events));
    }

    /** Executive: Get all RSVPs for one of their events. */
    @GetMapping("/api/executive/events/{eventID}/rsvps")
    public ResponseEntity<ApiResponse<Object>> getEventRsvps(
            @PathVariable String eventID,
            Authentication authentication) {

        var rsvps = proposalService.getEventRsvps(eventID, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("RSVPs retrieved.", rsvps));
    }

    @PostMapping("/api/executive/events/{eventID}/rsvps/{studentNumber}/check-in")
    public ResponseEntity<ApiResponse<Object>> manuallyCheckIn(
            @PathVariable String eventID,
            @PathVariable String studentNumber,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Attendance confirmed.",
                proposalService.manuallyCheckIn(eventID, studentNumber, authentication.getName())));
    }

    /** Executive: Edit a PUBLISHED event (requires change reason, notifies attendees). */
    @PutMapping("/api/executive/events/{eventID}/edit-published")
    public ResponseEntity<ApiResponse<EventProposalResponseDTO>> editPublishedEvent(
            @PathVariable String eventID,
            @RequestBody java.util.Map<String, Object> payload,
            Authentication authentication) {

        EventProposalResponseDTO response = proposalService.editPublishedEvent(
                eventID, authentication.getName(), payload);

        return ResponseEntity.ok(ApiResponse.success(
                "Event updated. Notifications sent to attendees.", response));
    }

    @PostMapping("/api/executive/events/{eventID}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<EventProposalResponseDTO>> cancelEvent(
            @PathVariable String eventID,
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {
        EventProposalResponseDTO response = proposalService.cancelPublishedEvent(
                eventID, authentication.getName(),
                payload == null ? null : (String) payload.get("reason"));
        return ResponseEntity.ok(ApiResponse.success(
                "Event cancelled. RSVP'd attendees have been notified.", response));
    }

    @PostMapping("/api/executive/events/{eventID}/postpone")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<EventProposalResponseDTO>> postponeEvent(
            @PathVariable String eventID,
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {
        EventProposalResponseDTO response = proposalService.postponePublishedEvent(
                eventID, authentication.getName(), payload);
        return ResponseEntity.ok(ApiResponse.success(
                "Event postponed. RSVP'd attendees have been notified.", response));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CO-HOST ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Executive responds to a co-host collaboration invitation. */
    @PostMapping("/api/executive/events/cohost/{invitationID}/respond")
    public ResponseEntity<ApiResponse<Void>> respondToCoHostInvitation(
            @PathVariable String invitationID,
            @RequestParam String action,
            Authentication authentication) {

        proposalService.respondToCoHostInvitation(
                invitationID, authentication.getName(), action);

        String message = "ACCEPT".equalsIgnoreCase(action)
                ? "Collaboration accepted."
                : "Collaboration declined.";

        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    // ═══════════════════════════════════════════════════════════════════════════
// SDO ENDPOINTS (continued)
// ═══════════════════════════════════════════════════════════════════════════

    /**
     * SDO gets events for their supervised societies with status filter.
     * GET /api/sdo/events/proposed?status=PROPOSED
     */
    @GetMapping("/api/sdo/events/proposed")
    @PreAuthorize("hasRole('SDO')")
    public ResponseEntity<ApiResponse<List<EventProposalResponseDTO>>> getProposedEvents(
            @RequestParam(required = false, defaultValue = "PROPOSED") String status,
            Authentication authentication) {

        List<EventProposalResponseDTO> events =
                proposalService.getProposedEventsForSDO(authentication.getName(), status);

        return ResponseEntity.ok(ApiResponse.success(
                "Events retrieved.", events));
    }
}
