package com.societycentral.repository;

import com.societycentral.model.Event;
import com.societycentral.model.EventStatus;
import com.societycentral.repository.projection.ExecutiveEventListProjection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Provides persistence and scheduling queries for events.
 */
@Repository
public interface EventRepository
        extends JpaRepository<Event, String> {

    // ID type = String (eventID)

    List<Event> findByEventStatus(
            EventStatus eventStatus
    );

    List<Event> findByEventStatusAndRsvpOpenNoticeSentAtIsNullAndRsvpOpenDateLessThanEqual(
            EventStatus eventStatus, java.time.LocalDateTime now);

    @Query("""
            select distinct e
            from Event e
            join Hoster h on h.id.eventID = e.eventID
            join h.society society
            where e.eventStatus = :status
              and h.isPrimary = true
              and society.sdoStaffNumber = :staffNumber
            order by e.eventDate asc, coalesce(e.eventStartTime, e.eventTime) asc, e.eventID asc
            """)
    List<Event> findProposalsForResponsibleSdo(
            @Param("status") EventStatus status,
            @Param("staffNumber") String staffNumber);

    /**
     * Returns PUBLISHED events visible to a student:
     * - Events hosted by societies they are a member of, OR
     * - Events open to every student (attendingType = EVERY_STUDENT)
     */
    @Query("""
            select e
            from Event e
            where e.eventStatus =
                  com.societycentral.model.EventStatus.PUBLISHED
              and e.rsvpOpenDate is not null
              and e.rsvpOpenDate <= :now
              and (
                    e.attendingType =
                       com.societycentral.model.AttendingType.EVERY_STUDENT
                    or exists (
                        select h.id.eventID
                        from Hoster h
                        where h.id.eventID = e.eventID
                          and h.id.societyID in :societyIDs
                    )
              )
            order by
                e.eventDate asc,
                coalesce(e.eventStartTime, e.eventTime) asc,
                e.eventID asc
            """)
    List<Event> findVisibleEventsForStudent(
            @Param("societyIDs")
            List<String> societyIDs,
            @Param("now") LocalDateTime now
    );

    /** Compatibility overload for existing repository callers/tests. */
    @Query("""
            select distinct e from Event e
            where e.eventStatus = com.societycentral.model.EventStatus.PUBLISHED
              and e.rsvpOpenDate is not null
              and e.rsvpOpenDate <= CURRENT_TIMESTAMP
            """)
    List<Event> findVisibleEventsForStudent(List<String> societyIDs);

    /**
     * Returns one published event only when it is visible to the authenticated
     * student through society membership or EVERY_STUDENT attendance.
     *
     * @param eventID requested event identifier
     * @param societyIDs societies the student belongs to
     * @return the visible event when authorised
     */
    @Query("""
            select distinct e
            from Event e
            join Hoster h on h.id.eventID = e.eventID
            where e.eventID = :eventID
              and e.eventStatus =
                  com.societycentral.model.EventStatus.PUBLISHED
              and e.rsvpOpenDate is not null
              and e.rsvpOpenDate <= :now
              and (
                    h.id.societyID in :societyIDs
                    or e.attendingType =
                       com.societycentral.model.AttendingType.EVERY_STUDENT
              )
            """)
    Optional<Event> findVisibleEventForStudent(
            @Param("eventID")
            String eventID,

            @Param("societyIDs")
            List<String> societyIDs,
            @Param("now") LocalDateTime now
    );

    /** Compatibility overload for existing repository callers/tests. */
    @Query("""
            select distinct e from Event e
            join Hoster h on h.id.eventID = e.eventID
            where e.eventID = :eventID
              and e.eventStatus = com.societycentral.model.EventStatus.PUBLISHED
              and e.rsvpOpenDate is not null
              and e.rsvpOpenDate <= CURRENT_TIMESTAMP
              and (h.id.societyID in :societyIDs
                   or e.attendingType = com.societycentral.model.AttendingType.EVERY_STUDENT)
            """)
    Optional<Event> findVisibleEventForStudent(String eventID, List<String> societyIDs);

    /**
     * Returns every published event in chronological order for A400 users
     * who have unrestricted published-event visibility.
     *
     * @return all published events in chronological order
     */
    @Query("""
            select e
            from Event e
            where e.eventStatus =
                  com.societycentral.model.EventStatus.PUBLISHED
            order by
                e.eventDate asc,
                coalesce(e.eventStartTime, e.eventTime) asc,
                e.eventID asc
            """)
    List<Event> findAllPublishedEvents();

    @Query("""
            select e from Event e
            where e.eventStatus = com.societycentral.model.EventStatus.PUBLISHED
              and e.rsvpOpenDate is not null
              and e.rsvpOpenDate <= :now
            order by e.eventDate asc, coalesce(e.eventStartTime, e.eventTime) asc, e.eventID asc
            """)
    List<Event> findAllVisiblePublishedEvents(@Param("now") LocalDateTime now);

    /**
     * Returns one event only when it is published.
     *
     * @param eventID requested event identifier
     * @return the published event when it exists
     */
    @Query("""
            select e
            from Event e
            where e.eventID = :eventID
              and e.eventStatus =
                  com.societycentral.model.EventStatus.PUBLISHED
            """)
    Optional<Event> findPublishedEvent(
            @Param("eventID")
            String eventID
    );

    @Query("""
            select e from Event e
            where e.eventID = :eventID
              and e.eventStatus = com.societycentral.model.EventStatus.PUBLISHED
              and e.rsvpOpenDate is not null
              and e.rsvpOpenDate <= :now
            """)
    Optional<Event> findVisiblePublishedEvent(
            @Param("eventID") String eventID,
            @Param("now") LocalDateTime now);

    /**
     * Returns all published events hosted by societies supervised by the
     * authenticated Student Development Officer.
     *
     * Results are ordered chronologically.
     */
    @Query("""
            select e
            from Event e
            where e.eventStatus =
                  com.societycentral.model.EventStatus.PUBLISHED
              and exists (
                    select h.id.eventID
                    from Hoster h
                    where h.id.eventID = e.eventID
                      and h.id.societyID in :societyIDs
              )
            order by
                e.eventDate asc,
                coalesce(e.eventStartTime, e.eventTime) asc,
                e.eventID asc
            """)
    List<Event> findVisibleEventsForSDO(
            @Param("societyIDs")
            List<String> societyIDs
    );

    /**
     * Returns one published event only when it belongs to a society supervised
     * by the authenticated Student Development Officer.
     *
     * @param eventID requested event identifier
     * @param societyIDs societies supervised by the SDO
     * @return the visible event when authorised
     */
    @Query("""
            select distinct e
            from Event e
            join Hoster h on h.id.eventID = e.eventID
            where e.eventID = :eventID
              and e.eventStatus =
                  com.societycentral.model.EventStatus.PUBLISHED
              and h.id.societyID in :societyIDs
            """)
    Optional<Event> findVisibleEventForSDO(
            @Param("eventID")
            String eventID,

            @Param("societyIDs")
            List<String> societyIDs
    );

    List<Event> findByEventDateGreaterThanEqualAndEventStatus(
            LocalDate fromDate,
            EventStatus eventStatus
    );

    @Query("""
            select count(event)
            from Event event
            join Hoster host on host.id.eventID = event.eventID
            where host.id.societyID = :societyID
              and event.eventStatus = com.societycentral.model.EventStatus.PUBLISHED
              and event.eventDate >= :currentDate
            """)
    long countUpcomingPublishedEventsForSociety(
            @Param("societyID") String societyID,
            @Param("currentDate") LocalDate currentDate);

    /**
     * Returns a bounded profile preview of upcoming published society events.
     */
    @Query("""
            select event
            from Hoster host
            join host.event event
            where host.id.societyID = :societyID
              and event.eventStatus =
                  com.societycentral.model.EventStatus.PUBLISHED
              and (
                    event.eventDate > :currentDate
                    or (
                        event.eventDate = :currentDate
                        and (
                            coalesce(
                                event.eventEndTime,
                                event.eventStartTime,
                                event.eventTime
                            ) is null
                            or coalesce(
                                event.eventEndTime,
                                event.eventStartTime,
                                event.eventTime
                            ) >= :currentTime
                        )
                    )
              )
            order by event.eventDate asc,
                     coalesce(event.eventStartTime, event.eventTime) asc,
                     event.eventID asc
            """)
    List<Event> findUpcomingPublishedEventsForSocietyProfile(
            @Param("societyID") String societyID,
            @Param("currentDate") LocalDate currentDate,
            @Param("currentTime") LocalTime currentTime,
            Pageable pageable);

    List<Event> findByEventCampus(
            String eventCampus
    );

    @Enumerated(EnumType.STRING)
    Integer countAllByEventDateBetweenAndEventStatus(
            LocalDate eventDate,
            LocalDate eventDate2,
            EventStatus eventStatus
    );

    /**
     * Returns one page of events linked to the authenticated executive's
     * society through Hoster.
     *
     * <p>Event, society and authoritative venue data are projected in one
     * query. The hard-coded ordering is the executive workflow's stable
     * default and uses eventID as a deterministic tie-breaker.</p>
     *
     * @param societyID authenticated executive's active society
     * @param status optional workflow-status filter
     * @param searchPattern optional lower-case LIKE pattern
     * @param pageable zero-based page and size
     * @return matching society events
     */
    @Query(
            value = """
                    select
                        e.eventID as eventID,
                        e.eventName as eventName,
                        e.eventDescription as eventDescription,
                        e.eventDate as eventDate,
                        coalesce(
                            e.eventStartTime,
                            e.eventTime
                        ) as eventStartTime,
                        e.eventEndTime as eventEndTime,
                        e.eventStatus as eventStatus,
                        e.eventCampus as campus,
                        e.venueCode as venueCode,
                        coalesce(
                            v.venueName,
                            e.eventVenue
                        ) as venueName,
                        v.capacity as venueCapacity,
                        e.eventLimit as eventLimit,
                        e.posterUrl as posterUrl,
                        e.bannerUrl as bannerUrl,
                        s.societyID as societyID,
                        s.societyName as societyName,
                        e.createdAt as createdAt,
                        e.updatedAt as updatedAt,
                        e.rejectionReason as rejectionReason
                    from Hoster h
                    join h.event e
                    join h.society s
                    left join Venue v
                        on v.venueCode = e.venueCode
                    where h.id.societyID = :societyID
                      and (
                            :status is null
                            or e.eventStatus = :status
                      )
                      and (
                            :searchPattern is null
                            or lower(e.eventName)
                               like :searchPattern
                            or lower(
                                coalesce(
                                    v.venueName,
                                    e.eventVenue
                                )
                            ) like :searchPattern
                      )
                    order by
                        e.updatedAt desc,
                        e.eventID asc
                    """,
            countQuery = """
                    select count(h)
                    from Hoster h
                    join h.event e
                    left join Venue v
                        on v.venueCode = e.venueCode
                    where h.id.societyID = :societyID
                      and (
                            :status is null
                            or e.eventStatus = :status
                      )
                      and (
                            :searchPattern is null
                            or lower(e.eventName)
                               like :searchPattern
                            or lower(
                                coalesce(
                                    v.venueName,
                                    e.eventVenue
                                )
                            ) like :searchPattern
                      )
                    """
    )
    Page<ExecutiveEventListProjection> findExecutiveEvents(
            @Param("societyID")
            String societyID,

            @Param("status")
            EventStatus status,

            @Param("searchPattern")
            String searchPattern,

            Pageable pageable
    );

    /**
     * Locks an Event row before an executive edit or status transition.
     *
     * @param eventID event identifier
     * @return locked event when it exists
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e
            from Event e
            where e.eventID = :eventID
            """)
    Optional<Event> findByIdForUpdate(
            @Param("eventID")
            String eventID
    );

    /**
     * Removes budget rows that reference an event's Hoster rows.
     */
    @Modifying
    @Query("""
            delete from BudgetRequest budgetRequest
            where budgetRequest.eventID = :eventID
            """)
    int deleteBudgetRequestsByEventID(
            @Param("eventID")
            String eventID
    );

    /**
     * Removes outcome rows before their composite Hoster parent is deleted.
     */
    @Modifying
    @Query("""
            delete from EventOutcome eventOutcome
            where eventOutcome.id.eventID = :eventID
            """)
    int deleteEventOutcomesByEventID(
            @Param("eventID")
            String eventID
    );

    /**
     * Removes RSVP rows that reference the event directly.
     */
    @Modifying
    @Query("""
            delete from RSVP rsvp
            where rsvp.id.eventID = :eventID
            """)
    int deleteRsvpsByEventID(
            @Param("eventID")
            String eventID
    );

    /**
     * Removes feedback rows that reference the event directly.
     */
    @Modifying
    @Query("""
            delete from EventFeedback feedback
            where feedback.eventID = :eventID
            """)
    int deleteEventFeedbackByEventID(
            @Param("eventID")
            String eventID
    );

    /**
     * Counts blocking events whose time range overlaps the requested range.
     *
     * Strict comparisons deliberately allow back-to-back reservations:
     * an event ending at the requested start, or starting at the requested
     * end, does not overlap.
     *
     * @param venueCode selected venue
     * @param eventDate requested date
     * @param requestedStart requested start time
     * @param requestedEnd requested end time
     * @param blockingStatuses statuses that reserve venues
     * @return number of conflicting events
     */
    @Query("""
            select count(e)
            from Event e
            where e.venueCode = :venueCode
              and e.eventDate = :eventDate
              and e.eventStatus in :blockingStatuses
              and e.eventStartTime < :requestedEnd
              and e.eventEndTime > :requestedStart
            """)
    long countVenueConflicts(
            @Param("venueCode")
            String venueCode,

            @Param("eventDate")
            LocalDate eventDate,

            @Param("requestedStart")
            LocalTime requestedStart,

            @Param("requestedEnd")
            LocalTime requestedEnd,

            @Param("blockingStatuses")
            Collection<EventStatus> blockingStatuses
    );

    /**
     * Counts overlaps while excluding the event currently being edited.
     *
     * @param currentEventID event being edited
     * @param venueCode selected venue
     * @param eventDate requested date
     * @param requestedStart requested start time
     * @param requestedEnd requested end time
     * @param blockingStatuses statuses that reserve venues
     * @return number of conflicting events other than the edited event
     */
    @Query("""
            select count(e)
            from Event e
            where e.eventID <> :currentEventID
              and e.venueCode = :venueCode
              and e.eventDate = :eventDate
              and e.eventStatus in :blockingStatuses
              and e.eventStartTime < :requestedEnd
              and e.eventEndTime > :requestedStart
            """)
    long countVenueConflictsExcludingEvent(
            @Param("currentEventID")
            String currentEventID,

            @Param("venueCode")
            String venueCode,

            @Param("eventDate")
            LocalDate eventDate,

            @Param("requestedStart")
            LocalTime requestedStart,

            @Param("requestedEnd")
            LocalTime requestedEnd,

            @Param("blockingStatuses")
            Collection<EventStatus> blockingStatuses
    );


    // In EventRepository.java - Add this method

    /**
     * Find PROPOSED events for societies supervised by an SDO
     * Used for the SDO Approve/Reject Events dashboard
     */
    @Query("""
    select distinct e
    from Event e
    join Hoster h on h.id.eventID = e.eventID
    where e.eventStatus = :status
      and h.id.societyID in :societyIDs
    order by e.createdAt desc
    """)
    List<Event> findEventsByStatusForSocieties(
            @Param("status")
            EventStatus status,

            @Param("societyIDs")
            List<String> societyIDs
    );
}
