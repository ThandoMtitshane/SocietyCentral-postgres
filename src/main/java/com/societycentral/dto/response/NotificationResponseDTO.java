package com.societycentral.dto.response;
import com.societycentral.model.Notification;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
@Getter @Builder
public class NotificationResponseDTO {
    private String notificationID, title, message, notifType, relatedID;
    private Boolean isRead;
    private LocalDateTime createdAt;
    public static NotificationResponseDTO from(Notification n) { return builder().notificationID(n.getNotificationID()).title(n.getTitle()).message(n.getMessage()).notifType(n.getNotifType() == null ? null : n.getNotifType().name()).relatedID(n.getRelatedID()).isRead(n.getIsRead()).createdAt(n.getCreatedAt()).build(); }
}
