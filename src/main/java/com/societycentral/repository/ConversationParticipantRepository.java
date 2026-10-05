package com.societycentral.repository;

import com.societycentral.model.ConversationParticipant;
import com.societycentral.model.ConversationParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence queries for conversation membership.
 */
@Repository
public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipant, ConversationParticipantId> {

    List<ConversationParticipant> findByIdConversationID(String conversationID);

    /** Active (not-left) participants of a conversation. */
    @Query("""
            select p
            from ConversationParticipant p
            where p.id.conversationID = :conversationID
              and p.leftAt is null
            """)
    List<ConversationParticipant> findActiveByConversationID(
            @Param("conversationID") String conversationID);

    Optional<ConversationParticipant>
    findByIdConversationIDAndIdStudentNumber(
            String conversationID, String studentNumber);

    /** True if the student is a current (not-left) participant. */
    @Query("""
            select count(p) > 0
            from ConversationParticipant p
            where p.id.conversationID = :conversationID
              and p.id.studentNumber = :studentNumber
              and p.leftAt is null
            """)
    boolean isActiveParticipant(
            @Param("conversationID") String conversationID,
            @Param("studentNumber") String studentNumber);

    /** Active student numbers in a conversation, for fan-out/notifications. */
    @Query("""
            select p.id.studentNumber
            from ConversationParticipant p
            where p.id.conversationID = :conversationID
              and p.leftAt is null
            """)
    List<String> findActiveStudentNumbers(
            @Param("conversationID") String conversationID);
}
