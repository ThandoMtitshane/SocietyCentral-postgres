package com.societycentral.controller;

import com.societycentral.dto.request.ReorderSocietyGalleryMediaRequestDTO;
import com.societycentral.dto.request.ReorderSocietyHighlightsRequestDTO;
import com.societycentral.dto.request.SocietyHighlightRequestDTO;
import com.societycentral.dto.request.UpdateSocietyPublicProfileRequestDTO;
import com.societycentral.dto.request.UpdateSocietyGalleryMediaRequestDTO;
import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.ExecutiveSocietyProfileDTO;
import com.societycentral.dto.response.SocietyGalleryMediaResponseDTO;
import com.societycentral.dto.response.SocietyHighlightResponseDTO;
import com.societycentral.dto.response.SocietyMediaUploadResponseDTO;
import com.societycentral.service.ExecutiveSocietyProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Authenticated executive management endpoints for the caller's own society.
 */
@RestController
@RequestMapping("/api/executive")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class ExecutiveSocietyProfileController {

    private final ExecutiveSocietyProfileService profileService;

    @GetMapping({
            "/society/profile",
            "/societies/{societyID}/profile"
    })
    public ResponseEntity<ApiResponse<ExecutiveSocietyProfileDTO>>
    getProfile(
            @PathVariable(required = false) String societyID,
            @AuthenticationPrincipal UserDetails userDetails) {
        ExecutiveSocietyProfileDTO profile = societyID == null
                ? profileService.getOwnProfile(userDetails.getUsername())
                : profileService.getProfile(
                        userDetails.getUsername(), societyID);
        return ResponseEntity.ok(ApiResponse.success(
                "Executive society profile retrieved successfully.",
                profile));
    }

    @PutMapping({
            "/society/profile",
            "/societies/{societyID}/profile"
    })
    public ResponseEntity<ApiResponse<ExecutiveSocietyProfileDTO>>
    updatePublicProfile(
            @PathVariable(required = false) String societyID,
            @Valid @RequestBody UpdateSocietyPublicProfileRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        ExecutiveSocietyProfileDTO profile =
                societyID == null
                        ? profileService.updatePublicProfile(
                                userDetails.getUsername(), request)
                        : profileService.updatePublicProfile(
                                userDetails.getUsername(),
                                societyID,
                                request);
        return ResponseEntity.ok(ApiResponse.success(
                "Society profile updated successfully.",
                profile));
    }

    @PostMapping(
            value = {
                    "/society/profile/logo",
                    "/societies/{societyID}/profile/logo"
            },
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SocietyMediaUploadResponseDTO>>
    uploadLogo(
            @PathVariable(required = false) String societyID,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        SocietyMediaUploadResponseDTO uploaded =
                societyID == null
                        ? profileService.uploadSocietyLogo(
                                userDetails.getUsername(), file)
                        : profileService.uploadSocietyLogo(
                                userDetails.getUsername(),
                                societyID,
                                file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Society logo uploaded successfully.",
                        uploaded));
    }

    @PostMapping(
            value = {
                    "/society/profile/banner",
                    "/societies/{societyID}/profile/banner"
            },
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SocietyMediaUploadResponseDTO>>
    uploadBanner(
            @PathVariable(required = false) String societyID,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        SocietyMediaUploadResponseDTO uploaded =
                societyID == null
                        ? profileService.uploadSocietyBanner(
                                userDetails.getUsername(), file)
                        : profileService.uploadSocietyBanner(
                                userDetails.getUsername(),
                                societyID,
                                file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Society banner uploaded successfully.",
                        uploaded));
    }

    @PostMapping(
            value = {
                    "/society/profile/gallery",
                    "/societies/{societyID}/profile/gallery"
            },
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SocietyGalleryMediaResponseDTO>>
    uploadGalleryImage(
            @PathVariable(required = false) String societyID,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String caption,
            @AuthenticationPrincipal UserDetails userDetails) {
        SocietyGalleryMediaResponseDTO uploaded =
                profileService.uploadGalleryImage(
                        userDetails.getUsername(),
                        societyID,
                        file,
                        caption);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Society gallery image uploaded successfully.",
                        uploaded));
    }

    @PutMapping({
            "/society/profile/gallery/{mediaID}",
            "/societies/{societyID}/profile/gallery/{mediaID}"
    })
    public ResponseEntity<ApiResponse<SocietyGalleryMediaResponseDTO>>
    updateGalleryCaption(
            @PathVariable(required = false) String societyID,
            @PathVariable String mediaID,
            @Valid @RequestBody UpdateSocietyGalleryMediaRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        SocietyGalleryMediaResponseDTO updated =
                profileService.updateGalleryCaption(
                        userDetails.getUsername(),
                        societyID,
                        mediaID,
                        request.getCaption());
        return ResponseEntity.ok(ApiResponse.success(
                "Society gallery caption updated successfully.",
                updated));
    }

    @PutMapping({
            "/society/profile/gallery/reorder",
            "/societies/{societyID}/profile/gallery/reorder"
    })
    public ResponseEntity<ApiResponse<List<SocietyGalleryMediaResponseDTO>>>
    reorderGallery(
            @PathVariable(required = false) String societyID,
            @Valid @RequestBody ReorderSocietyGalleryMediaRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<SocietyGalleryMediaResponseDTO> reordered =
                profileService.reorderGallery(
                        userDetails.getUsername(),
                        societyID,
                        request.getMediaIDs());
        return ResponseEntity.ok(ApiResponse.success(
                "Society gallery reordered successfully.",
                reordered));
    }

    @DeleteMapping({
            "/society/profile/gallery/{mediaID}",
            "/societies/{societyID}/profile/gallery/{mediaID}"
    })
    public ResponseEntity<ApiResponse<Void>> deleteGalleryImage(
            @PathVariable(required = false) String societyID,
            @PathVariable String mediaID,
            @AuthenticationPrincipal UserDetails userDetails) {
        profileService.deleteGalleryImage(
                userDetails.getUsername(), societyID, mediaID);
        return ResponseEntity.ok(ApiResponse.success(
                "Society gallery image removed successfully.",
                null));
    }

    @PostMapping(
            value = {
                    "/society/profile/highlights",
                    "/societies/{societyID}/profile/highlights"
            },
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SocietyHighlightResponseDTO>>
    createHighlight(
            @PathVariable(required = false) String societyID,
            @Valid @RequestPart("highlight")
                    SocietyHighlightRequestDTO request,
            @RequestPart(value = "coverImage", required = false)
                    MultipartFile coverImage,
            @AuthenticationPrincipal UserDetails userDetails) {
        SocietyHighlightResponseDTO created = profileService.createHighlight(
                userDetails.getUsername(),
                societyID,
                request,
                coverImage);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Society highlight created successfully.",
                        created));
    }

    @PutMapping(
            value = {
                    "/society/profile/highlights/{highlightID}",
                    "/societies/{societyID}/profile/highlights/{highlightID}"
            },
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SocietyHighlightResponseDTO>>
    updateHighlight(
            @PathVariable(required = false) String societyID,
            @PathVariable String highlightID,
            @Valid @RequestPart("highlight")
                    SocietyHighlightRequestDTO request,
            @RequestPart(value = "coverImage", required = false)
                    MultipartFile coverImage,
            @AuthenticationPrincipal UserDetails userDetails) {
        SocietyHighlightResponseDTO updated = profileService.updateHighlight(
                userDetails.getUsername(),
                societyID,
                highlightID,
                request,
                coverImage);
        return ResponseEntity.ok(ApiResponse.success(
                "Society highlight updated successfully.",
                updated));
    }

    @PutMapping({
            "/society/profile/highlights/reorder",
            "/societies/{societyID}/profile/highlights/reorder"
    })
    public ResponseEntity<ApiResponse<List<SocietyHighlightResponseDTO>>>
    reorderHighlights(
            @PathVariable(required = false) String societyID,
            @Valid @RequestBody ReorderSocietyHighlightsRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<SocietyHighlightResponseDTO> reordered =
                profileService.reorderHighlights(
                        userDetails.getUsername(),
                        societyID,
                        request.getHighlightIDs());
        return ResponseEntity.ok(ApiResponse.success(
                "Society highlights reordered successfully.",
                reordered));
    }

    @DeleteMapping({
            "/society/profile/highlights/{highlightID}",
            "/societies/{societyID}/profile/highlights/{highlightID}"
    })
    public ResponseEntity<ApiResponse<Void>> deleteHighlight(
            @PathVariable(required = false) String societyID,
            @PathVariable String highlightID,
            @AuthenticationPrincipal UserDetails userDetails) {
        profileService.deleteHighlight(
                userDetails.getUsername(), societyID, highlightID);
        return ResponseEntity.ok(ApiResponse.success(
                "Society highlight deleted successfully.",
                null));
    }
}
