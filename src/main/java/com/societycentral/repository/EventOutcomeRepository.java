package com.societycentral.repository;

import com.societycentral.model.EventOutcome;
import com.societycentral.model.EventOutcomeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventOutcomeRepository extends JpaRepository<EventOutcome, EventOutcomeId> {
    // ID type = EventOutcomeId (eventID, societyID)

    // All outcome submissions for a given event (one per co-hosting society)
    List<EventOutcome> findByIdEventID(String eventID);

    // All outcomes submitted on behalf of a given society
    List<EventOutcome> findByIdSocietyID(String societyID);
}
