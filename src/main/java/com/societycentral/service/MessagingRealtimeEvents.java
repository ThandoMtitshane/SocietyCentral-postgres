package com.societycentral.service;

import com.societycentral.dto.response.ConversationSummaryView;
import com.societycentral.dto.response.MessageView;

import java.util.List;

/** Events queued by messaging transactions and delivered only after commit. */
record MessagingMessageChangedEvent(
        List<String> recipientEmails,
        MessageView message) {
}

record MessagingConversationChangedEvent(
        String recipientEmail,
        ConversationSummaryView summary) {
}
