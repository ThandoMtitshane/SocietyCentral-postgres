package com.societycentral.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends STOMP updates after the database transaction commits. Realtime
 * delivery remains best-effort and cannot roll back a successful REST write.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingRealtimeListener {

    private final MessagingRealtimePort realtimePort;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void messageChanged(MessagingMessageChangedEvent event) {
        try {
            realtimePort.broadcastMessage(event.recipientEmails(), event.message());
        } catch (Exception ex) {
            log.warn("Realtime message delivery failed after commit: {}",
                    ex.getMessage());
        }
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void conversationChanged(MessagingConversationChangedEvent event) {
        try {
            realtimePort.broadcastConversationUpdate(
                    event.recipientEmail(), event.summary());
        } catch (Exception ex) {
            log.warn("Realtime conversation delivery failed after commit: {}",
                    ex.getMessage());
        }
    }
}
