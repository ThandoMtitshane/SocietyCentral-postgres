package com.societycentral.repository;

import com.societycentral.model.ConversationSdoParticipant;
import com.societycentral.model.ConversationSdoParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Persistence queries for SDO membership of institutional conversations. */
@Repository
public interface ConversationSdoParticipantRepository
        extends JpaRepository<ConversationSdoParticipant, ConversationSdoParticipantId> {

    List<ConversationSdoParticipant> findByIdConversationID(String conversationID);

    @Query("""
            select p
            from ConversationSdoParticipant p
            where p.id.conversationID = :conversationID
              and p.leftAt is null
            """)
    List<ConversationSdoParticipant> findActiveByConversationID(
            @Param("conversationID") String conversationID);

    Optional<ConversationSdoParticipant>
    findByIdConversationIDAndIdSdoStaffNumber(
            String conversationID, String sdoStaffNumber);

    @Query("""
            select count(p) > 0
            from ConversationSdoParticipant p
            where p.id.conversationID = :conversationID
              and p.id.sdoStaffNumber = :sdoStaffNumber
              and p.leftAt is null
            """)
    boolean isActiveParticipant(
            @Param("conversationID") String conversationID,
            @Param("sdoStaffNumber") String sdoStaffNumber);

    @Query("""
            select p.id.sdoStaffNumber
            from ConversationSdoParticipant p
            where p.id.conversationID = :conversationID
              and p.leftAt is null
            """)
    List<String> findActiveSdoStaffNumbers(
            @Param("conversationID") String conversationID);
}
