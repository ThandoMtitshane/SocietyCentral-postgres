package com.societycentral.service;

import com.societycentral.model.Notification;
import com.societycentral.model.NotificationType;
import com.societycentral.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRealtimeBroadcaster realtimeBroadcaster;

    @Autowired
    public NotificationService(NotificationRepository notificationRepository,
                               NotificationRealtimeBroadcaster realtimeBroadcaster) {
        this.notificationRepository = notificationRepository;
        this.realtimeBroadcaster = realtimeBroadcaster;
    }

    public List<Notification> findForUser(String recipientEmail) {
        return notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(recipientEmail);
    }

    public List<Notification> findUnreadForUser(String recipientEmail) {
        return notificationRepository.findByRecipientEmailAndIsReadFalse(recipientEmail);
    }

    public long countUnread(String recipientEmail) {
        return notificationRepository.countByRecipientEmailAndIsReadFalse(recipientEmail);
    }

    public Optional<Notification> findById(String notificationID) {
        return notificationRepository.findById(notificationID);
    }

    public Notification create(Notification notification) {
        notification.setCreatedAt(LocalDateTime.now());
        notification.setIsRead(false);

        // NOTE: This table is the IN-APP notification feed only (see
        // schema comments). Actually SENDING an email/push for this
        // notification - if desired - happens in a separate mail/push
        // service (e.g. MailService using spring-boot-starter-mail),
        // triggered alongside this save(), not inside this method.
        Notification saved = notificationRepository.save(notification);

        // Nudge the recipient over STOMP so the notification bell refreshes
        // instantly (covers messages, announcements and event updates), rather
        // than waiting for the client's periodic poll. Best-effort.
        realtimeBroadcaster.signalRecipient(saved.getRecipientEmail());

        return saved;
    }

    @org.springframework.transaction.annotation.Transactional
    public Notification markAsReadForUser(String notificationID, String email) {
        Notification notification = notificationRepository.findById(notificationID)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationID));
        if (!email.equalsIgnoreCase(notification.getRecipientEmail())) {
            throw new org.springframework.security.access.AccessDeniedException("Notification does not belong to the authenticated user.");
        }
        notification.setIsRead(true);
        return notificationRepository.save(notification);
    }

    public boolean notificationExists(String recipientEmail,
                                      NotificationType notificationType,
                                      String relatedID) {
        return notificationRepository.existsByRecipientEmailAndNotifTypeAndRelatedID(
                recipientEmail, notificationType, relatedID);
    }

    @org.springframework.transaction.annotation.Transactional
    public void createAnnouncementNotificationIfAbsent(String recipientEmail, String societyName, String subject,
                                                       String announcementID) {
        if (notificationExists(recipientEmail, NotificationType.ANNOUNCEMENT, announcementID)) return;
        Notification notification = new Notification();
        notification.setNotificationID("NTF" + UUID.randomUUID().toString().replace("-", "").substring(0, 17));
        notification.setRecipientEmail(recipientEmail);
        notification.setTitle("New announcement from " + (societyName == null ? "Society Central" : societyName));
        notification.setMessage(subject);
        notification.setNotifType(NotificationType.ANNOUNCEMENT);
        notification.setRelatedID(announcementID);
        create(notification);
    }

    /** Legacy service entry point retained for existing internal callers. */
    @org.springframework.transaction.annotation.Transactional
    public Notification markAsRead(String notificationID) {
        Notification notification = notificationRepository.findById(notificationID)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationID));
        notification.setIsRead(true);
        return notificationRepository.save(notification);
    }

    @org.springframework.transaction.annotation.Transactional
    public int markAllAsRead(String email) { return notificationRepository.markAllRead(email); }

    public void deleteById(String notificationID) {
        notificationRepository.deleteById(notificationID);
    }

    // TODO: TaskService's scheduled reminder job and AnnouncementService's
    // fan-out both call create() here to populate this feed - see TODOs in
    // those services.
}
