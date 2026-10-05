package com.societycentral.repository;

import com.societycentral.model.MessageMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persistence queries for structured message mentions.
 */
@Repository
public interface MessageMentionRepository
        extends JpaRepository<MessageMention, String> {

    List<MessageMention> findByMessageID(String messageID);

    List<MessageMention> findByMessageIDIn(List<String> messageIDs);
}
