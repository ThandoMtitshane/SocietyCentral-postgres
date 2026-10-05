package com.societycentral.service;

import com.societycentral.model.EventFeedback;
import com.societycentral.repository.EventFeedbackRepository;
import com.societycentral.repository.RSVPRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

@Service
public class EventFeedbackService {

    private final EventFeedbackRepository eventFeedbackRepository;
    private final RSVPRepository rsvpRepository;

    @Autowired
    public EventFeedbackService(EventFeedbackRepository eventFeedbackRepository,
                                 RSVPRepository rsvpRepository) {
        this.eventFeedbackRepository = eventFeedbackRepository;
        this.rsvpRepository = rsvpRepository;
    }

    public List<EventFeedback> findAll() {
        return eventFeedbackRepository.findAll();
    }

    public Optional<EventFeedback> findById(String feedbackID) {
        return eventFeedbackRepository.findById(feedbackID);
    }

    public List<EventFeedback> findByEventID(String eventID) {
        return eventFeedbackRepository.findByEventID(eventID);
    }

    public List<EventFeedback> findByStudentNumber(String studentNumber) {
        return eventFeedbackRepository.findByStudentNumber(studentNumber);
    }

    public EventFeedback submitFeedback(EventFeedback feedback) {
        // BUSINESS RULE (enforced here at the APPLICATION layer, per
        // earlier decision - NOT enforceable via FK alone):
        // "Only students who have ATTENDED the event may submit feedback."
        //
        // "Attended" = there exists an RSVP for (feedback.getEventID(),
        // feedback.getStudentNumber()) with scannedStatus == true.
        boolean attended = rsvpRepository
                .findById(new com.societycentral.model.RsvpId(
                        feedback.getEventID(), feedback.getStudentNumber()))
                .map(rsvp -> Boolean.TRUE.equals(rsvp.getScannedStatus()))
                .orElse(false);

        if (!attended) {
            throw new IllegalStateException(
                    "Only students who attended (scanned QR) may submit feedback for this event");
        }

        // TODO: consider also enforcing one feedback submission per
        // (eventID, studentNumber) using
        // eventFeedbackRepository.existsByEventIDAndStudentNumber(...).
        return eventFeedbackRepository.save(feedback);
    }

    public void deleteById(String feedbackID) {
        eventFeedbackRepository.deleteById(feedbackID);
    }

    public OptionalDouble computeAverageRating(String eventID) {
        // Used to populate Event.overallRating (a DERIVED value - see
        // EventService TODO).
        return eventFeedbackRepository.findByEventID(eventID)
                .stream()
                .filter(f -> f.getRating() != null)
                .mapToInt(EventFeedback::getRating)
                .average();
    }
}
