package com.societycentral.service;

import com.societycentral.model.EventOutcome;
import com.societycentral.model.EventOutcomeId;
import com.societycentral.repository.EventOutcomeRepository;
import com.societycentral.repository.HosterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EventOutcomeService {

    private final EventOutcomeRepository eventOutcomeRepository;
    private final HosterRepository hosterRepository;

    @Autowired
    public EventOutcomeService(EventOutcomeRepository eventOutcomeRepository,
                                HosterRepository hosterRepository) {
        this.eventOutcomeRepository = eventOutcomeRepository;
        this.hosterRepository = hosterRepository;
    }

    public List<EventOutcome> findAll() {
        return eventOutcomeRepository.findAll();
    }

    public Optional<EventOutcome> findById(EventOutcomeId id) {
        return eventOutcomeRepository.findById(id);
    }

    public List<EventOutcome> findByEventID(String eventID) {
        return eventOutcomeRepository.findByIdEventID(eventID);
    }

    public EventOutcome submitOutcome(EventOutcome outcome) {
        // BUSINESS RULE: "For each event, only ONE designated member of
        // EACH hosting society's executive may provide an event outcome
        // evaluation" - i.e. one EventOutcome row per (eventID, societyID),
        // and that (eventID, societyID) pair MUST exist in Hoster.
        //
        // The composite FK (EventOutcome -> Hoster) and PK (eventID,
        // societyID) enforce most of this at the DB level. What remains for
        // this service / ExecutiveService to verify:
        //
        // TODO: verify outcome.getStudentNumber() is a CURRENT executive of
        // outcome.getId().getSocietyID() during outcome.getTermStartDate()
        // (inject ExecutiveService / use Executive composite key lookup -
        // this is the FK-enforced relationship, but double-checking
        // "currently serving" status may need application logic too).
        return eventOutcomeRepository.save(outcome);
    }

    public void deleteById(EventOutcomeId id) {
        eventOutcomeRepository.deleteById(id);
    }

    public boolean isReportComplete(String eventID) {
        // BUSINESS RULE: "Event reports must be generated using both
        // attendance data and feedback data" - and per our Hoster
        // discussion, a report is only complete once EVERY hosting society
        // has submitted its outcome.
        //
        // Returns true if every Hoster row for this event has a
        // corresponding EventOutcome row.
        long hostCount = hosterRepository.findByIdEventID(eventID).size();
        long outcomeCount = eventOutcomeRepository.findByIdEventID(eventID).size();
        return hostCount > 0 && hostCount == outcomeCount;
    }

    // TODO: a "pendingOutcomeSocieties(eventID)" method returning which
    // hosting societies have NOT yet submitted would be useful for
    // C903: Send Task reminder / executive dashboards. Compute as:
    // Hoster societies for this event MINUS EventOutcome societies for
    // this event.
}
