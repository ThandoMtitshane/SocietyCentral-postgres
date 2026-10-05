package com.societycentral.repository;

import com.societycentral.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persistence queries for chat messages.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    /** Paged message history for a conversation, newest first. */
    Page<Message> findByConversationIDOrderByCreatedAtDesc(
            String conversationID, Pageable pageable);

    /** The most recent message in a conversation (for inbox previews). */
    Optional<Message> findFirstByConversationIDOrderByCreatedAtDesc(
            String conversationID);

    /**
     * Counts messages a participant has not yet read in one conversation:
     * messages after their {@code lastReadAt} that they did not send.
     * A null {@code lastReadAt} counts every message from other senders.
     */
    @Query("""
            select count(m)
            from Message m
            where m.conversationID = :conversationID
              and (m.senderStudentNumber is null
                   or m.senderStudentNumber <> :studentNumber)
              and (:lastReadAt is null or m.createdAt > :lastReadAt)
            """)
    long countUnread(
            @Param("conversationID") String conversationID,
            @Param("studentNumber") String studentNumber,
            @Param("lastReadAt") LocalDateTime lastReadAt);

    /** Unread count for an SDO participant in an institutional channel. */
    @Query("""
            select count(m)
            from Message m
            where m.conversationID = :conversationID
              and (m.senderSdoStaffNumber is null
                   or m.senderSdoStaffNumber <> :sdoStaffNumber)
              and (:lastReadAt is null or m.createdAt > :lastReadAt)
            """)
    long countUnreadForSdo(
            @Param("conversationID") String conversationID,
            @Param("sdoStaffNumber") String sdoStaffNumber,
            @Param("lastReadAt") LocalDateTime lastReadAt);

    List<Message> findByConversationIDInOrderByCreatedAtDesc(
            List<String> conversationIDs);
}
