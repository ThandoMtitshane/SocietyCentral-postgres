package com.societycentral.service;

import com.societycentral.dto.request.EventFeedbackRequestDTO;
import com.societycentral.dto.request.ExecutiveEventReportDTO;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Handles submission of post-event feedback (students) and reports (executives).
 *
 * Business rules:
 * - Students: must have a persisted checked-in RSVP (QR or manual)
 * - Students: can only submit once per event
 * - Executives: only 1 report per society per event
 * - Executives: must be an active exec of the hosting society
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventFeedbackSubmissionService {

    private final EventFeedbackRepository eventFeedbackRepository;
    private final ExecutiveEventReportRepository execReportRepository;
    private final RSVPRepository rsvpRepository;
    private final EventRepository eventRepository;
    private final StudentRepository studentRepository;
    private final ExecutiveRepository executiveRepository;
    private final HosterRepository hosterRepository;
    private final Clock clock;

    @Transactional
    public void submitStudentFeedback(EventFeedbackRequestDTO request, String email) {
        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));

        String eventID = request.getEventID();
        String studentNumber = student.getStudentNumber();

        // Validate event exists and has finished.
        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));

        if (!isFeedbackWindowOpen(event)) {
            throw new IllegalStateException(
                    "The 5-day feedback window for this event has closed.");
        }

        // Attendance is the canonical persisted RSVP check-in state. Both QR
        // and manual Executive check-in set this state.
        RsvpId rsvpId = new RsvpId(eventID, studentNumber);
        RSVP rsvp = rsvpRepository.findById(rsvpId).orElse(null);

        if (rsvp == null || !Boolean.TRUE.equals(rsvp.getScannedStatus())) {
            throw new IllegalStateException(
                    "Only students who attended this event can submit feedback.");
        }

        // Check for duplicate
        if (eventFeedbackRepository.existsByEventIDAndStudentNumber(eventID, studentNumber)) {
            throw new IllegalStateException("You have already submitted feedback for this event.");
        }

        // Save feedback
        EventFeedback feedback = new EventFeedback();
        feedback.setFeedbackID("EF-" + UUID.randomUUID().toString().substring(0, 8));
        feedback.setStudentNumber(studentNumber);
        feedback.setEventID(eventID);
        feedback.setRating(request.getRating());
        feedback.setDescription(request.getDescription());
        feedback.setOrganizationRating(request.getOrganizationRating());
        feedback.setVenueRating(request.getVenueRating());
        feedback.setContentRating(request.getContentRating());
        feedback.setWouldRecommend(request.getWouldRecommend());
        feedback.setHighlights(request.getHighlights());
        feedback.setImprovements(request.getImprovements());
        feedback.setSubmittedAt(LocalDateTime.now(clock));

        eventFeedbackRepository.save(feedback);
        log.info("Student feedback submitted: student={} event={}", studentNumber, eventID);
    }

    @Transactional
    public void updateStudentFeedback(EventFeedbackRequestDTO request, String email) {
        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
        Event event = eventRepository.findById(request.getEventID())
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));
        if (!isFeedbackWindowOpen(event)) {
            throw new IllegalStateException("The 5-day feedback window for this event has closed.");
        }
        RSVP rsvp = rsvpRepository.findById(new RsvpId(request.getEventID(), student.getStudentNumber())).orElse(null);
        if (rsvp == null || !Boolean.TRUE.equals(rsvp.getScannedStatus())) {
            throw new IllegalStateException("Only users who attended this event can edit feedback.");
        }
        EventFeedback feedback = eventFeedbackRepository.findByEventIDAndStudentNumber(
                        request.getEventID(), student.getStudentNumber())
                .orElseThrow(() -> new IllegalArgumentException("Feedback not found."));
        feedback.setRating(request.getRating());
        feedback.setOrganizationRating(request.getOrganizationRating());
        feedback.setVenueRating(request.getVenueRating());
        feedback.setContentRating(request.getContentRating());
        feedback.setWouldRecommend(request.getWouldRecommend());
        feedback.setHighlights(request.getHighlights());
        feedback.setImprovements(request.getImprovements());
        feedback.setDescription(request.getDescription());
        eventFeedbackRepository.save(feedback);
    }

    public LocalDateTime feedbackDeadline(Event event) {
        LocalDateTime completion = event.getEventDate().atTime(
                event.getEventEndTime() != null ? event.getEventEndTime() : LocalTime.MAX);
        return completion.plusDays(5);
    }

    public boolean isFeedbackWindowOpen(Event event) {
        return event != null && event.getEventDate() != null
                && !LocalDateTime.now(clock).isAfter(feedbackDeadline(event));
    }

    public boolean isFeedbackEditable(Event event, EventFeedback feedback) {
        return feedback != null && isFeedbackWindowOpen(event);
    }

    /** Returns true only after the event's persisted end time has passed. */
    public boolean isEventFinished(Event event) {
        if (event == null || event.getEventDate() == null) return false;
        if (event.getEventEndTime() == null) {
            return event.getEventDate().isBefore(LocalDate.now(clock));
        }
        return !LocalDateTime.now(clock).isBefore(
                LocalDateTime.of(event.getEventDate(), event.getEventEndTime()));
    }

    @Transactional
    public void submitExecutiveReport(ExecutiveEventReportDTO request, String email) {
        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));

        String eventID = request.getEventID();

        // Verify event exists
        eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));

        // Must be active executive of a hosting society
        Executive activeRole = executiveRepository
                .findByIdStudentNumber(student.getStudentNumber())
                .stream()
                .filter(e -> e.getTermEndDate() == null || e.getTermEndDate().isAfter(java.time.LocalDate.now()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("You are not an active executive."));

        String societyID = activeRole.getId().getSocietyID();

        // Verify the society hosts this event
        if (!hosterRepository.existsByIdEventIDAndIdSocietyID(eventID, societyID)) {
            throw new IllegalStateException("Your society is not hosting this event.");
        }

        // Check for duplicate (1 per society per event)
        if (execReportRepository.existsByEventIDAndSocietyID(eventID, societyID)) {
            throw new IllegalStateException(
                    "An event report has already been submitted for this event by your society.");
        }

        // Save report
        ExecutiveEventReport report = new ExecutiveEventReport();
        report.setReportID(ExecutiveEventReport.newID());
        report.setEventID(eventID);
        report.setSocietyID(societyID);
        report.setStudentNumber(student.getStudentNumber());
        report.setExpectations(request.getExpectations());
        report.setExpectationsMet(request.getExpectationsMet());
        report.setSuccessAssessment(request.getSuccessAssessment());
        report.setSuccessReason(request.getSuccessReason());
        report.setImprovements(request.getImprovements());
        report.setAdvice(request.getAdvice());
        report.setAttendeeCount(request.getAttendeeCount());
        report.setOverallRating(request.getOverallRating());
        report.setAdditionalNotes(request.getAdditionalNotes());
        report.setSubmittedAt(LocalDateTime.now(clock));

        execReportRepository.save(report);
        log.info("Executive report submitted: exec={} society={} event={}",
                student.getStudentNumber(), societyID, eventID);
    }

    @Transactional(readOnly = true)
    public boolean hasStudentSubmittedFeedback(String eventID, String email) {
        Student student = studentRepository.findByEmail(email).orElse(null);
        if (student == null) return false;
        return eventFeedbackRepository.existsByEventIDAndStudentNumber(eventID, student.getStudentNumber());
    }

    @Transactional(readOnly = true)
    public boolean hasExecutiveSubmittedReport(String eventID, String email) {
        Student student = studentRepository.findByEmail(email).orElse(null);
        if (student == null) return false;

        return executiveRepository.findByIdStudentNumber(student.getStudentNumber())
                .stream()
                .filter(e -> e.getTermEndDate() == null || e.getTermEndDate().isAfter(java.time.LocalDate.now()))
                .anyMatch(e -> execReportRepository.existsByEventIDAndSocietyID(
                        eventID, e.getId().getSocietyID()));
    }
}
