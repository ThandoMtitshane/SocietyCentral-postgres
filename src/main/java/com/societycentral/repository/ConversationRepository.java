package com.societycentral.repository;

import com.societycentral.model.Conversation;
import com.societycentral.model.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence queries for messaging conversations.
 */
@Repository
public interface ConversationRepository
        extends JpaRepository<Conversation, String> {

    /** Finds the single auto-managed group conversation for a society. */
    Optional<Conversation> findByTypeAndSocietyID(
            ConversationType type, String societyID);

    /**
     * Returns the conversation IDs the given student currently participates in
     * (has not left), most recently active first. Used to build the inbox.
     */
    @Query("""
            select c.conversationID
            from ConversationParticipant p
            join Conversation c on c.conversationID = p.id.conversationID
            where p.id.studentNumber = :studentNumber
              and p.leftAt is null
              and c.status <> com.societycentral.model.ConversationStatus.REJECTED
            order by
              case when c.lastMessageAt is null then c.createdAt
                   else c.lastMessageAt end desc
            """)
    List<String> findActiveConversationIdsForStudent(
            @Param("studentNumber") String studentNumber);

    /** Institutional conversations in which the SDO currently participates. */
    @Query("""
            select c.conversationID
            from ConversationSdoParticipant p
            join Conversation c on c.conversationID = p.id.conversationID
            where p.id.sdoStaffNumber = :sdoStaffNumber
              and p.leftAt is null
              and c.status <> com.societycentral.model.ConversationStatus.REJECTED
            order by
              case when c.lastMessageAt is null then c.createdAt
                   else c.lastMessageAt end desc
            """)
    List<String> findActiveConversationIdsForSdo(
            @Param("sdoStaffNumber") String sdoStaffNumber);

    /**
     * Finds an existing one-on-one direct conversation between exactly two
     * students, regardless of who started it. Returns any status so a prior
     * rejected/pending request can be detected.
     */
    @Query("""
            select c
            from Conversation c
            where c.type = com.societycentral.model.ConversationType.DIRECT
              and exists (
                    select 1 from ConversationParticipant p1
                    where p1.id.conversationID = c.conversationID
                      and p1.id.studentNumber = :studentA)
              and exists (
                    select 1 from ConversationParticipant p2
                    where p2.id.conversationID = c.conversationID
                      and p2.id.studentNumber = :studentB)
            """)
    List<Conversation> findDirectConversationsBetween(
            @Param("studentA") String studentA,
            @Param("studentB") String studentB);

    /**
     * Direct conversations between one executive (student) and one SDO,
     * regardless of who initiated.
     */
    @Query("""
            select c
            from Conversation c
            where c.type = com.societycentral.model.ConversationType.DIRECT
              and exists (
                    select 1 from ConversationParticipant p
                    where p.id.conversationID = c.conversationID
                      and p.id.studentNumber = :studentNumber)
              and exists (
                    select 1 from ConversationSdoParticipant sp
                    where sp.id.conversationID = c.conversationID
                      and sp.id.sdoStaffNumber = :sdoStaffNumber)
            """)
    List<Conversation> findDirectConversationsBetweenStudentAndSdo(
            @Param("studentNumber") String studentNumber,
            @Param("sdoStaffNumber") String sdoStaffNumber);

    /** Direct conversations between two SDOs, regardless of who initiated. */
    @Query("""
            select c
            from Conversation c
            where c.type = com.societycentral.model.ConversationType.DIRECT
              and exists (
                    select 1 from ConversationSdoParticipant s1
                    where s1.id.conversationID = c.conversationID
                      and s1.id.sdoStaffNumber = :sdoA)
              and exists (
                    select 1 from ConversationSdoParticipant s2
                    where s2.id.conversationID = c.conversationID
                      and s2.id.sdoStaffNumber = :sdoB)
            """)
    List<Conversation> findDirectConversationsBetweenSdos(
            @Param("sdoA") String sdoA,
            @Param("sdoB") String sdoB);
}
