package com.societycentral.controller;

import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.RSVPResponseDTO;
import com.societycentral.service.RSVPService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * RSVP endpoints for students to confirm event attendance.
 *
 * Supports two flows:
 * 1. Token-based (no login): student clicks unique link from email
 *    POST /api/events/{eventID}/rsvp?token=...
 *
 * 2. Authenticated (logged in): student RSVPs from the event details page
 *    POST /api/events/{eventID}/rsvp (with JWT in Authorization header)
 *
 * Both paths use the same endpoint; the service layer resolves
 * the student identity from whichever credential is present.
 */
@RestController
@RequestMapping("/api/events")
public class RSVPController {

    private final RSVPService rsvpService;

    @Autowired
    public RSVPController(RSVPService rsvpService) {
        this.rsvpService = rsvpService;
    }

    /**
     * Confirms a student's RSVP for an event.
     *
     * @param eventID the event to RSVP for
     * @param token   optional RSVP invitation token (from email link)
     * @param authentication optional Spring Security authentication (logged-in flow)
     * @return RSVP confirmation details including QR code reference
     */
    @PostMapping("/{eventID}/rsvp")
    public ResponseEntity<ApiResponse<RSVPResponseDTO>> confirmRsvp(
            @PathVariable String eventID,
            @RequestParam(required = false) String token,
            Authentication authentication) {

        RSVPResponseDTO response = rsvpService.confirmRsvp(eventID, token, authentication);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("RSVP confirmed successfully. Your ticket has been sent to your email.", response));
    }

    /**
     * Returns the RSVP status for the current student on a given event.
     * Used by the frontend to know whether to show "RSVP" or "Already RSVP'd".
     *
     * @param eventID the event to check
     * @param token   optional RSVP token
     * @param authentication optional authentication
     * @return RSVP details if already RSVP'd, or 404 if not
     */
    @GetMapping("/{eventID}/rsvp")
    public ResponseEntity<ApiResponse<RSVPResponseDTO>> getRsvpStatus(
            @PathVariable String eventID,
            @RequestParam(required = false) String token,
            Authentication authentication) {

        RSVPResponseDTO response = rsvpService.getRsvpStatus(eventID, token, authentication);

        if (response == null) {
            return ResponseEntity.ok(
                    ApiResponse.success("No RSVP found for this event.", null));
        }

        return ResponseEntity.ok(
                ApiResponse.success("RSVP found.", response));
    }

    /**
     * Downloads the PDF ticket for an existing RSVP.
     *
     * @param eventID the event
     * @param token   optional RSVP token
     * @param authentication optional authentication
     * @return PDF file as byte array
     */
    @GetMapping("/{eventID}/rsvp/ticket")
    public ResponseEntity<byte[]> downloadTicket(
            @PathVariable String eventID,
            @RequestParam(required = false) String token,
            Authentication authentication) {

        byte[] pdfBytes = rsvpService.generateTicketPdf(eventID, token, authentication);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment",
                "SocietyCentral_Ticket_" + eventID + ".pdf");

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    /**
     * Scans a QR code ticket to confirm attendance at the door.
     * Validates that the ticket belongs to the selected event.
     *
     * @param qrCodeTicket the QR code content scanned from the ticket
     * @param eventID the event currently being managed (for validation)
     * @return success with student name, or error if invalid/already used/wrong event
     */
    @PostMapping("/scan-qr")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> scanQrCode(
            @RequestParam String qrCodeTicket,
            @RequestParam(required = false) String eventID,
            Authentication authentication) {

        // First look up the RSVP by QR code to validate event match
        var rsvpOpt = rsvpService.findByQrCodeTicket(qrCodeTicket);
        if (rsvpOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid QR code");
        }

        var rsvp = rsvpOpt.get();

        // Validate that this ticket is for the selected event
        if (eventID != null && !eventID.isBlank()
                && !rsvp.getId().getEventID().equals(eventID.trim())) {
            throw new IllegalArgumentException(
                    "This ticket is for a different event and does not match the selected event.");
        }

        // Now scan (marks as attended)
        var scanned = rsvpService.scanQrCode(qrCodeTicket, authentication == null ? null : authentication.getName());

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("studentNumber", scanned.getId().getStudentNumber());
        result.put("eventID", scanned.getId().getEventID());
        result.put("scannedAt", scanned.getScannedAt());

        // Resolve student name
        if (scanned.getStudent() != null && scanned.getStudent().getUser() != null) {
            result.put("studentName",
                    scanned.getStudent().getUser().getFirstName() + " "
                            + scanned.getStudent().getUser().getLastName());
        }

        return ResponseEntity.ok(ApiResponse.success("Attendance confirmed.", result));
    }

    /** Manually checks in one RSVP, scoped to the authenticated executive's society. */
    @PostMapping("/{eventID}/guests/{studentNumber}/check-in")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> checkInGuest(
            @PathVariable String eventID,
            @PathVariable String studentNumber,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authentication is required.");
        }

        var checkedIn = rsvpService.checkInByStudent(
                eventID, studentNumber, authentication.getName());
        var result = new java.util.HashMap<String, Object>();
        result.put("eventID", checkedIn.getId().getEventID());
        result.put("studentNumber", checkedIn.getId().getStudentNumber());
        result.put("scannedAt", checkedIn.getScannedAt());
        result.put("checkInMethod", checkedIn.getCheckInMethod());
        result.put("checkedInBy", checkedIn.getCheckedInBy());
        return ResponseEntity.ok(ApiResponse.success(
                "Attendance confirmed.", result));
    }
}
