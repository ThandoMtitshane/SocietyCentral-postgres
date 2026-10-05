package com.societycentral.service;

import com.societycentral.dto.response.ConversationSummaryView;
import com.societycentral.dto.response.MessageView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * STOMP implementation of {@link MessagingRealtimePort}. Pushes live message
 * and inbox updates to each recipient's personal queue so clients update
 * without polling.
 *
 * <p>Destinations (per authenticated user, keyed by email = STOMP principal
 * name):</p>
 * <ul>
 *   <li>{@code /user/queue/messages} - a new or updated {@link MessageView}</li>
 *   <li>{@code /user/queue/conversations} - an updated
 *       {@link ConversationSummaryView} for the inbox</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessagingBroadcaster implements MessagingRealtimePort {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void broadcastMessage(
            List<String> recipientEmails, MessageView message) {
        if (recipientEmails == null) {
            return;
        }
        for (String email : recipientEmails) {
            if (email == null) {
                continue;
            }
            try {
                messagingTemplate.convertAndSendToUser(
                        email, "/queue/messages", message);
            } catch (Exception ex) {
                log.warn("Failed to push message to {}: {}",
                        email, ex.getMessage());
            }
        }
    }

    @Override
    public void broadcastConversationUpdate(
            String recipientEmail, ConversationSummaryView summary) {
        if (recipientEmail == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(
                    recipientEmail, "/queue/conversations", summary);
        } catch (Exception ex) {
            log.warn("Failed to push conversation update to {}: {}",
                    recipientEmail, ex.getMessage());
        }
    }
}
