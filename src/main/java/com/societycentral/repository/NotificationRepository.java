package com.societycentral.repository;

import com.societycentral.model.Notification;
import com.societycentral.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    // ID type = String (notificationID)

    List<Notification> findByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

    List<Notification> findByRecipientEmailAndIsReadFalse(String recipientEmail);

    long countByRecipientEmailAndIsReadFalse(String recipientEmail);

    boolean existsByRecipientEmailAndNotifTypeAndRelatedID(
            String recipientEmail, NotificationType notifType, String relatedID);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.recipientEmail = :email and n.isRead = false")
    int markAllRead(@Param("email") String email);

    @Modifying(flushAutomatically = true)
    @Query("update Notification n set n.recipientEmail = :newEmail " +
            "where n.recipientEmail = :oldEmail")
    int reassignRecipientEmail(@Param("oldEmail") String oldEmail,
                               @Param("newEmail") String newEmail);
}
