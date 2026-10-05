package com.societycentral.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Pushes a lightweight "your notifications changed" signal to a specific user
 * over STOMP, so the in-app notification bell can refresh instantly instead of
 * waiting for the periodic poll. This covers messages, announcements, and event
 * updates alike, since they all surface through the notification feed.
 *
 * <p>The payload is intentionally minimal - the client re-fetches the
 * authoritative notification list/count on receipt. Delivery is best-effort:
 * failures are logged and swallowed so notification persistence never depends
 * on the socket.</p>
 */
@Component
@Slf4j
public class NotificationRealtimeBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationRealtimeBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Signals the recipient (by email = STOMP principal name) that a new
     * notification was created. Clients subscribe to
     * {@code /user/queue/notifications}.
     *
     * @param recipientEmail the user whose feed changed
     */
    public void signalRecipient(String recipientEmail) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(
                    recipientEmail,
                    "/queue/notifications",
                    Map.of("type", "NOTIFICATIONS_CHANGED",
                            "at", Instant.now().toString()));
        } catch (Exception ex) {
            log.warn("Failed to push notification signal to {}: {}",
                    recipientEmail, ex.getMessage());
        }
    }
}
