package com.societycentral.controller;
import com.societycentral.dto.response.*;
import com.societycentral.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/notifications")
public class NotificationController {
 private final NotificationService service;
 @GetMapping public ResponseEntity<ApiResponse<List<NotificationResponseDTO>>> all(Authentication a){ return ResponseEntity.ok(ApiResponse.success("Notifications loaded.", service.findForUser(a.getName()).stream().map(NotificationResponseDTO::from).toList())); }
 @GetMapping("/unread-count") public ResponseEntity<ApiResponse<Long>> count(Authentication a){ return ResponseEntity.ok(ApiResponse.success("Unread notification count loaded.", service.countUnread(a.getName()))); }
 @PatchMapping("/{id}/read") public ResponseEntity<ApiResponse<NotificationResponseDTO>> read(@PathVariable String id, Authentication a){ return ResponseEntity.ok(ApiResponse.success("Notification marked as read.", NotificationResponseDTO.from(service.markAsReadForUser(id,a.getName())))); }
 @PatchMapping("/read-all") public ResponseEntity<ApiResponse<Integer>> readAll(Authentication a){ return ResponseEntity.ok(ApiResponse.success("Notifications marked as read.", service.markAllAsRead(a.getName()))); }
}
