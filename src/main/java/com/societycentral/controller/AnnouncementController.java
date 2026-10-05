package com.societycentral.controller;

import com.societycentral.dto.request.AnnouncementRequestDTO;
import com.societycentral.dto.response.AnnouncementResponseDTO;
import com.societycentral.dto.response.ApiResponse;
import com.societycentral.model.Announcement;
import com.societycentral.model.Society;
import com.societycentral.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<AnnouncementResponseDTO>>> myAnnouncements(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Announcements loaded.", announcementService.findForViewer(authentication.getName()).stream().map(a -> toResponse(a, false, authentication.getName())).toList()));
    }

    @GetMapping("/manage")
    public ResponseEntity<ApiResponse<List<AnnouncementResponseDTO>>> managedAnnouncements(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Managed announcements loaded.", announcementService.findForManager(authentication.getName()).stream().map(a -> toResponse(a, true, authentication.getName())).toList()));
    }

    @GetMapping("/{announcementID}")
    public ResponseEntity<ApiResponse<AnnouncementResponseDTO>> announcement(@PathVariable String announcementID, Authentication authentication) {
        Announcement announcement = announcementService.findById(announcementID).orElseThrow(() -> new IllegalArgumentException("Announcement not found."));
        if (!announcementService.canView(authentication.getName(), announcement) && !announcementService.canManage(authentication.getName(), announcement)) throw new IllegalStateException("You are not authorised to view this announcement.");
        boolean manager = announcementService.canManage(authentication.getName(), announcement);
        return ResponseEntity.ok(ApiResponse.success("Announcement loaded.", toResponse(announcement, manager, authentication.getName())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AnnouncementResponseDTO>> createAnnouncement(
            @Valid @RequestBody AnnouncementRequestDTO request,
            Authentication authentication) {
        Announcement announcement = toAnnouncement(request, authentication.getName());
        Announcement savedAnnouncement = announcementService.create(announcement);
        AnnouncementResponseDTO response = toResponse(savedAnnouncement, true, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Announcement created successfully.",
                        response));
    }

    @PutMapping("/{announcementID}")
    public ResponseEntity<ApiResponse<AnnouncementResponseDTO>> updateAnnouncement(
            @PathVariable String announcementID, @Valid @RequestBody AnnouncementRequestDTO request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Announcement updated successfully.",
                toResponse(announcementService.update(announcementID, request, authentication.getName()), true, authentication.getName())));
    }

    @DeleteMapping("/{announcementID}")
    public ResponseEntity<ApiResponse<AnnouncementResponseDTO>> removeAnnouncement(@PathVariable String announcementID, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Announcement removed successfully.", toResponse(announcementService.remove(announcementID, authentication.getName()), true, authentication.getName())));
    }

    private static Announcement toAnnouncement(
            AnnouncementRequestDTO request,
            String senderEmail) {
        Announcement announcement = new Announcement();
        announcement.setSubject(request.getSubject().trim());
        announcement.setDescription(request.getDescription());
        announcement.setTargetType(request.getTargetType());
        announcement.setExpireDate(request.getExpireDate());
        announcement.setPublishAt(request.getPublishAt());
        announcement.setSentBy(senderEmail);

        String societyID = request.getSocietyID();
        if (societyID != null && !societyID.isBlank()) {
            Society society = new Society();
            society.setSocietyID(societyID.trim());
            announcement.setSociety(society);
        }

        return announcement;
    }

    private AnnouncementResponseDTO toResponse(Announcement announcement, boolean management, String email) {
        return AnnouncementResponseDTO.builder()
                .announcementID(announcement.getAnnouncementID())
                .subject(announcement.getSubject())
                .description(announcement.getDescription())
                .targetType(announcement.getTargetType())
                .societyID(
                        announcement.getSociety() == null
                                ? null
                                : announcement.getSociety().getSocietyID())
                .societyName(
                        announcement.getSociety() == null
                                ? null
                                : announcement.getSociety().getSocietyName())
                .societyLogoUrl(
                        announcement.getSociety() == null
                                ? null
                                : announcement.getSociety().getLogoUrl())
                .sentBy(announcement.getSentBy())
                .datePosted(announcement.getDatePosted())
                .publishAt(announcement.getPublishAt())
                .expireDate(announcement.getExpireDate())
                .fromDisplayName(!management ? null : announcement.getSentByUser() == null ? announcement.getSentBy() :
                        (announcement.getSentByUser().getFirstName() + " " + announcement.getSentByUser().getLastName()).trim())
                .status(announcement.isRemoved() ? "REMOVED" : announcement.getPublishAt() != null && java.time.LocalDateTime.now().isBefore(announcement.getPublishAt()) ? "SCHEDULED" : (announcement.getExpireDate() != null && !java.time.LocalDateTime.now().isBefore(announcement.getExpireDate()) ? "EXPIRED" : "ACTIVE"))
                .removed(management && announcement.isRemoved()).removedAt(management ? announcement.getRemovedAt() : null).removedBy(management ? announcement.getRemovedBy() : null)
                .canEdit(management && !announcement.isRemoved() && announcement.getExpireDate() != null && announcement.getExpireDate().isAfter(java.time.LocalDateTime.now()) && announcementService.canEditOrRemove(email, announcement))
                .canRemove(management && !announcement.isRemoved() && announcement.getExpireDate() != null && announcement.getExpireDate().isAfter(java.time.LocalDateTime.now()) && announcementService.canEditOrRemove(email, announcement))
                .build();
    }
}
