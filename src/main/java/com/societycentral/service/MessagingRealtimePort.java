package com.societycentral.service;

import com.societycentral.dto.response.ConversationSummaryView;
import com.societycentral.dto.response.MessageView;

import java.util.List;

/**
 * Outbound port for pushing live messaging updates to connected clients.
 *
 * <p>Defined as an interface so {@link MessagingService} does not depend on the
 * WebSocket infrastructure directly. The STOMP implementation
 * ({@code MessagingBroadcaster}) fans events out to each recipient's personal
 * queue. Delivery is invoked after commit and the concrete broadcaster treats
 * socket failures as best-effort, so persistence does not depend on STOMP.</p>
 */
public interface MessagingRealtimePort {

    /**
     * Delivers a new or updated message to each recipient. The per-recipient
     * summary reflects that recipient's own unread count and view flags.
     *
     * @param recipientEmails emails of participants to notify
     * @param message the message payload (already tailored is not required;
     *                per-viewer flags are recomputed client-side where needed)
     */
    void broadcastMessage(List<String> recipientEmails, MessageView message);

    /**
     * Notifies a recipient that one of their conversation summaries changed
     * (new message, read state, or request accepted/rejected), so inboxes can
     * update live without a refetch.
     */
    void broadcastConversationUpdate(
            String recipientEmail, ConversationSummaryView summary);
}
