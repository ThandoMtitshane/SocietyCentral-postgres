package com.societycentral.repository;

import com.societycentral.model.Hoster;
import com.societycentral.model.HosterId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HosterRepository extends JpaRepository<Hoster, HosterId> {

    // All societies hosting/co-hosting a given event
    List<Hoster> findByIdEventID(String eventID);

    // Loads the primary society for a batch of events without one query per event.
    List<Hoster> findByIdEventIDInAndIsPrimaryTrue(List<String> eventIDs);

    // All events a given society hosts/co-hosts
    List<Hoster> findByIdSocietyID(String societyID);

    /**
     * Loads an event and its society only when the Hoster relationship proves
     * that the event belongs to the supplied society.
     */
    @Query("""
            select h
            from Hoster h
            join fetch h.event
            join fetch h.society
            where h.id.eventID = :eventID
              and h.id.societyID = :societyID
            """)
    Optional<Hoster> findEventHostedBySociety(
            @Param("eventID") String eventID,
            @Param("societyID") String societyID);

    /**
     * Checks ownership without loading event details.
     */
    boolean existsByIdEventIDAndIdSocietyID(
            String eventID,
            String societyID);

    /**
     * Events hosted/co-hosted by a society whose name matches an optional
     * search term. Used for the @event mention autocomplete, which is limited
     * to the sender's own society. Ordered by most recent event first.
     */
    @Query("""
            select h.event
            from Hoster h
            where h.id.societyID = :societyID
              and (:searchPattern is null
                   or lower(h.event.eventName) like :searchPattern)
            order by h.event.createdAt desc
            """)
    List<com.societycentral.model.Event> findMentionableEventsForSociety(
            @Param("societyID") String societyID,
            @Param("searchPattern") String searchPattern);
}

