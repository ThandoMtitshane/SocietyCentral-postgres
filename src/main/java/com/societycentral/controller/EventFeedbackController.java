package com.societycentral.controller;

import com.societycentral.dto.request.EventFeedbackRequestDTO;
import com.societycentral.dto.request.ExecutiveEventReportDTO;
import com.societycentral.dto.response.ApiResponse;
import com.societycentral.service.EventFeedbackSubmissionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints for post-event feedback and executive reports.
 *
 * Student feedback:  POST /api/events/{eventID}/feedback
 * Executive report:  POST /api/events/{eventID}/report
 */
@RestController
@RequestMapping("/api/events")
public class EventFeedbackController {

    private final EventFeedbackSubmissionService feedbackService;

    @Autowired
    public EventFeedbackController(EventFeedbackSubmissionService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /**
     * Student submits event feedback (only if they attended — QR was scanned).
     */
    @PostMapping("/{eventID}/feedback")
    public ResponseEntity<ApiResponse<Void>> submitFeedback(
            @PathVariable String eventID,
            @Valid @RequestBody EventFeedbackRequestDTO request,
            Authentication authentication) {

        request.setEventID(eventID);
        feedbackService.submitStudentFeedback(request, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thank you for your feedback!", null));
    }

    @PutMapping("/{eventID}/feedback")
    public ResponseEntity<ApiResponse<Void>> updateFeedback(
            @PathVariable String eventID,
            @Valid @RequestBody EventFeedbackRequestDTO request,
            Authentication authentication) {
        request.setEventID(eventID);
        feedbackService.updateStudentFeedback(request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Feedback updated successfully.", null));
    }

    /**
     * Executive submits post-event report (1 per society per event).
     */
    @PostMapping("/{eventID}/report")
    public ResponseEntity<ApiResponse<Void>> submitReport(
            @PathVariable String eventID,
            @Valid @RequestBody ExecutiveEventReportDTO request,
            Authentication authentication) {

        request.setEventID(eventID);
        feedbackService.submitExecutiveReport(request, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Event report submitted successfully.", null));
    }

    /**
     * Check if student already submitted feedback for this event.
     */
    @GetMapping("/{eventID}/feedback/status")
    public ResponseEntity<ApiResponse<Boolean>> checkFeedbackStatus(
            @PathVariable String eventID,
            Authentication authentication) {

        boolean submitted = feedbackService.hasStudentSubmittedFeedback(
                eventID, authentication.getName());

        return ResponseEntity.ok(ApiResponse.success(
                submitted ? "Feedback already submitted." : "No feedback yet.", submitted));
    }

    /**
     * Check if executive already submitted report for this event.
     */
    @GetMapping("/{eventID}/report/status")
    public ResponseEntity<ApiResponse<Boolean>> checkReportStatus(
            @PathVariable String eventID,
            Authentication authentication) {

        boolean submitted = feedbackService.hasExecutiveSubmittedReport(
                eventID, authentication.getName());

        return ResponseEntity.ok(ApiResponse.success(
                submitted ? "Report already submitted." : "No report yet.", submitted));
    }
}
