package com.societycentral.repository;

import com.societycentral.model.RSVP;
import com.societycentral.model.RsvpId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RSVPRepository extends JpaRepository<RSVP, RsvpId> {
    // ID type = RsvpId (eventID, studentNumber)

    // All RSVPs for a given event (used for totalRSVP / attendance counts)
    List<RSVP> findByIdEventID(String eventID);

    // All RSVPs made by a given student
    List<RSVP> findByIdStudentNumber(String studentNumber);

    // Look up by QR code when scanning at the door
    Optional<RSVP> findByQrCodeTicket(String qrCodeTicket);

    // Count of scanned (attended) RSVPs for an event
    long countByIdEventIDAndScannedStatusTrue(String eventID);

    // Count confirmed RSVPs for an event (for capacity checking)
    long countByIdEventID(String eventID);

    // Check if a specific student already has an RSVP for an event
    boolean existsById(RsvpId id);

    /**
     * Pessimistic lock on all RSVP rows for an event.
     * Used during RSVP creation to prevent race conditions on capacity.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select count(r)
            from RSVP r
            where r.id.eventID = :eventID
            """)
    long countByEventIDWithLock(@Param("eventID") String eventID);
}
