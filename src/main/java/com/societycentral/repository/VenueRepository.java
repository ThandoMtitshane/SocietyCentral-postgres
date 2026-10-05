package com.societycentral.repository;

import com.societycentral.model.Campus;
import com.societycentral.model.EventStatus;
import com.societycentral.model.Venue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Provides persistence and availability access for authoritative venues.
 */
@Repository
public interface VenueRepository extends JpaRepository<Venue, String> {

    /**
     * Locks one venue row for the duration of the surrounding transaction.
     *
     * @param venueCode venue identifier
     * @return the locked venue when it exists
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venue v where v.venueCode = :venueCode")
    Optional<Venue> findByVenueCodeForUpdate(
            @Param("venueCode") String venueCode);

    /**
     * Finds active venues on a campus that do not overlap a blocking event.
     *
     * @param campus selected campus
     * @param eventDate requested event date
     * @param requestedStart requested start time
     * @param requestedEnd requested end time
     * @param blockingStatuses workflow statuses that reserve venues
     * @return available venues ordered by name
     */
    @Query("""
            select v
            from Venue v
            where v.active = true
              and v.campus = :campus
              and not exists (
                  select e.eventID
                  from Event e
                  where e.venueCode = v.venueCode
                    and e.eventDate = :eventDate
                    and e.eventStatus in :blockingStatuses
                    and e.eventStartTime < :requestedEnd
                    and e.eventEndTime > :requestedStart
              )
            order by v.venueName
            """)
    List<Venue> findAvailableVenues(
            @Param("campus") Campus campus,
            @Param("eventDate") LocalDate eventDate,
            @Param("requestedStart") LocalTime requestedStart,
            @Param("requestedEnd") LocalTime requestedEnd,
            @Param("blockingStatuses") Collection<EventStatus> blockingStatuses);

    /**
     * Finds active venues available while ignoring the event being edited.
     *
     * <p>The exclusion is applied inside the overlap subquery. Every other
     * blocking event for the same venue and strict time intersection still
     * removes that venue from the result.</p>
     *
     * @param campus selected campus
     * @param eventDate requested event date
     * @param requestedStart requested start time
     * @param requestedEnd requested end time
     * @param excludeEventID event whose existing reservation is ignored
     * @param blockingStatuses workflow statuses that reserve venues
     * @return available venues ordered by name
     */
    @Query("""
            select v
            from Venue v
            where v.active = true
              and v.campus = :campus
              and not exists (
                  select e.eventID
                  from Event e
                  where e.venueCode = v.venueCode
                    and e.eventDate = :eventDate
                    and e.eventStatus in :blockingStatuses
                    and e.eventID <> :excludeEventID
                    and e.eventStartTime < :requestedEnd
                    and e.eventEndTime > :requestedStart
              )
            order by v.venueName
            """)
    List<Venue> findAvailableVenuesExcludingEvent(
            @Param("campus") Campus campus,
            @Param("eventDate") LocalDate eventDate,
            @Param("requestedStart") LocalTime requestedStart,
            @Param("requestedEnd") LocalTime requestedEnd,
            @Param("excludeEventID") String excludeEventID,
            @Param("blockingStatuses") Collection<EventStatus> blockingStatuses);
}
