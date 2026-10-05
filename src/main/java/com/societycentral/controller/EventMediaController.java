package com.societycentral.controller;

import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.EventMediaUploadResponseDTO;
import com.societycentral.model.EventImageType;
import com.societycentral.service.EventMediaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Handles authenticated executive uploads of event posters and banners.
 */
@RestController
@RequestMapping("/api/executive/events")
public class EventMediaController {

    private final EventMediaService eventMediaService;

    /**
     * Creates the event-media controller.
     *
     * @param eventMediaService media workflow service
     */
    public EventMediaController(EventMediaService eventMediaService) {
        this.eventMediaService = eventMediaService;
    }

    /**
     * Validates and stores one poster or banner image.
     *
     * @param file multipart image file
     * @param imageType POSTER or BANNER
     * @param userDetails authenticated account supplied by Spring Security
     * @return stored media metadata in the standard API response
     */
    @PostMapping(
            value = "/media",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<EventMediaUploadResponseDTO>> uploadEventMedia(
            @RequestPart("file") MultipartFile file,
            @RequestParam("imageType") EventImageType imageType,
            @AuthenticationPrincipal UserDetails userDetails) {

        EventMediaUploadResponseDTO uploaded = eventMediaService.upload(
                userDetails.getUsername(),
                file,
                imageType);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Event image uploaded successfully.",
                        uploaded));
    }
}
