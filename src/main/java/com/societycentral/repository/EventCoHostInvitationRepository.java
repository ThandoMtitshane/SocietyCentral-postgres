package com.societycentral.repository;

import com.societycentral.model.EventCoHostInvitation;
import com.societycentral.model.EventCoHostStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventCoHostInvitationRepository extends JpaRepository<EventCoHostInvitation, String> {

    // All invitations for a given event
    List<EventCoHostInvitation> findByEventID(String eventID);

    // All invitations for a given event with a specific status
    List<EventCoHostInvitation> findByEventIDAndStatus(String eventID, EventCoHostStatus status);

    // All pending invitations for a society (inbox)
    List<EventCoHostInvitation> findByInvitedSocietyIDAndStatus(String societyID, EventCoHostStatus status);

    // Check if an active invitation already exists for event+society
    Optional<EventCoHostInvitation> findByEventIDAndInvitedSocietyIDAndStatusNot(
            String eventID, String invitedSocietyID, EventCoHostStatus excludedStatus);

    // Find a specific invitation for responding
    Optional<EventCoHostInvitation> findByInvitationIDAndInvitedSocietyID(
            String invitationID, String invitedSocietyID);

    // Delete all invitations for an event (used when event is deleted)
    void deleteByEventID(String eventID);
}
