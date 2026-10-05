package com.societycentral.controller;

import com.societycentral.dto.request.POARequestDTO;
import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.POAResponseDTO;
import com.societycentral.service.POAService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class POAController {

    private final POAService poaService;

    @GetMapping("/")
    public String test() {
        return "Hello from Spring Boot";
    }
    // ── Executive ─────────────────────────────────────────────────────────────

    /** Read-only view of current year POA,  all statuses visible */
    @GetMapping("/api/executive/poa/view")
    public ResponseEntity<ApiResponse<POAResponseDTO>> viewPOA(
            @AuthenticationPrincipal UserDetails userDetails) {
        POAResponseDTO result = poaService.getCurrentPOA(userDetails.getUsername());
        if (result == null)
            return ResponseEntity.ok(ApiResponse.success("No POA found for this year.", null));
        return ResponseEntity.ok(ApiResponse.success("POA retrieved.", result));
    }

    /** Get current year POA for the edit/save flow */
    @GetMapping("/api/executive/poa/current")
    public ResponseEntity<ApiResponse<POAResponseDTO>> getCurrentPOA(
            @AuthenticationPrincipal UserDetails userDetails) {
        POAResponseDTO result = poaService.getCurrentPOA(userDetails.getUsername());
        if (result == null)
            return ResponseEntity.ok(ApiResponse.success("No POA found for this year.", null));
        return ResponseEntity.ok(ApiResponse.success("POA retrieved.", result));
    }

    /** Save as DRAFT or SUBMIT for SDO review */
    @PostMapping("/api/executive/poa")
    public ResponseEntity<ApiResponse<POAResponseDTO>> saveOrSubmit(
            @RequestBody POARequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        POAResponseDTO result = poaService.saveOrSubmit(request, userDetails.getUsername());
        boolean submitting = "SUBMIT".equalsIgnoreCase(request.getAction());
        return ResponseEntity.ok(ApiResponse.success(
                submitting ? "POA submitted for SDO review."
                           : "POA draft saved successfully.", result));
    }

    // ── SDO ───────────────────────────────────────────────────────────────────

    /** Society cards with POA status for management overview page */
    @GetMapping("/api/sdo/poa/overview")
    public ResponseEntity<ApiResponse<List<POAService.POASocietyCardDTO>>> getOverview(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("POA overview retrieved.",
                poaService.getPOAManagementOverview(userDetails.getUsername())));
    }

    /** View POA for a specific society (null = no POA yet) */
    @GetMapping("/api/sdo/poa")
    public ResponseEntity<ApiResponse<POAResponseDTO>> getPOAForSociety(
            @RequestParam String societyID,
            @AuthenticationPrincipal UserDetails userDetails) {
        POAResponseDTO result = poaService.getPOAForSociety(
                societyID, userDetails.getUsername());
        if (result == null)
            return ResponseEntity.ok(ApiResponse.success(
                    "No POA submitted for this society yet.", null));
        return ResponseEntity.ok(ApiResponse.success("POA retrieved.", result));
    }

    /** All POAs for SDO's assigned societies */
    @GetMapping("/api/sdo/poa/all")
    public ResponseEntity<ApiResponse<List<POAResponseDTO>>> getAllPOAsForSDO(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("POAs retrieved.",
                poaService.getAllPOAsForSDO(userDetails.getUsername())));
    }

    /**
     * Review a POA.
     * Body: { "action": "APPROVE|REQUEST_REVISION",
     *         "reviewNotes": "...",
     *         "eventComments": [{ "poaEventID": "...", "comment": "..." }] }
     */
    @PostMapping("/api/sdo/poa/{poaID}/review")
    public ResponseEntity<ApiResponse<POAResponseDTO>> reviewPOA(
            @PathVariable String poaID,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        String action = (String) body.get("action");
        String reviewNotes = (String) body.getOrDefault("reviewNotes", "");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> rawComments =
                (List<Map<String, String>>) body.getOrDefault("eventComments", List.of());
        List<POAService.SDOEventComment> eventComments = rawComments.stream()
                .map(m -> new POAService.SDOEventComment(
                        m.get("poaEventID"), m.get("comment")))
                .toList();
        POAResponseDTO result = poaService.reviewPOA(
                poaID, action, reviewNotes, eventComments, userDetails.getUsername());
        String message = "APPROVE".equalsIgnoreCase(action)
                ? "POA approved successfully."
                : "Revision requested. Executives will be notified.";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    /** Send reminder to society (8-day cooldown enforced in service) */
    @PostMapping("/api/sdo/poa/{poaID}/remind")
    public ResponseEntity<ApiResponse<Void>> sendReminder(
            @PathVariable String poaID,
            @AuthenticationPrincipal UserDetails userDetails) {
        poaService.sendReminder(poaID, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(
                "Reminder sent to the society's executives.", null));
    }

    /** Request POA from a society that has not submitted one yet */
    @PostMapping("/api/sdo/poa/society/{societyID}/request")
    public ResponseEntity<ApiResponse<Void>> requestPOA(
            @PathVariable String societyID,
            @AuthenticationPrincipal UserDetails userDetails) {
        poaService.requestPOA(societyID, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(
                "POA request sent to the society's executives.", null));
    }
}