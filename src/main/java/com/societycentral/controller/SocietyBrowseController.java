package com.societycentral.controller;

import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.SocietyAnnouncementSummaryDTO;
import com.societycentral.dto.response.SocietyHighlightResponseDTO;
import com.societycentral.dto.response.SocietyProfileResponse;
import com.societycentral.dto.response.SocietyBrowseSummaryResponseDTO;
import com.societycentral.dto.response.StudentSocietyProfileDTO;
import com.societycentral.service.SocietyBrowseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for A500: Browse Societies.
 *
 * This controller provides read-only endpoints that allow authenticated
 * users to browse societies and receive a profile scoped to their role.
 *
 * Endpoints:
 *   GET /api/societies
 *       - lists all active societies.
 *
 *   GET /api/societies/{societyID}
 *       - retrieves one role-scoped society profile. Only SDOs may inspect
 *         inactive societies.
 *
 *   GET /api/societies/{societyID}/public
 *       - retrieves the reusable public-safe profile irrespective of the
 *         authenticated viewer's executive or SDO role.
 */
@RestController
@RequestMapping("/api/societies")
@RequiredArgsConstructor
public class SocietyBrowseController {

    private final SocietyBrowseService societyBrowseService;

    /**
     * Retrieves compact summaries of all active societies.
     *
     * SocietyBrowseSummaryResponseDTO is used because the Browse Societies page only
     * needs the information required to display society cards.
     *
     * @return active society summaries.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SocietyBrowseSummaryResponseDTO>>>
    getActiveSocieties() {

        List<SocietyBrowseSummaryResponseDTO> societies =
                societyBrowseService.getActiveSocieties();

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Active societies retrieved successfully.",
                        societies
                )
        );
    }

    /**
     * Retrieves a role-aware profile for one society.
     *
     * @param societyID unique identifier of the selected society.
     * @param authentication authenticated Spring Security principal.
     * @return profile fields authorised for the caller.
     */
    @GetMapping("/{societyID}")
    public ResponseEntity<ApiResponse<SocietyProfileResponse>>
    getSocietyProfile(
            @PathVariable String societyID,
            Authentication authentication) {

        // 1. Resolve the authenticated caller
        String authenticatedEmail = authentication.getName();

        // 2. Give SDOs oversight access, including inactive societies
        SocietyProfileResponse society;
        if (hasRole(authentication, "SDO")) {
            society = societyBrowseService.getSDOSocietyProfile(societyID);

        // 3. Give executives private data only for the requested society
        } else if (societyBrowseService.isActiveExecutiveOfSociety(
                authenticatedEmail, societyID)) {
            society = societyBrowseService.getExecutiveSocietyProfile(
                    societyID, authenticatedEmail);

        // 4. All other student accounts, including executives browsing a
        //    different society, receive public data plus A100 viewer state.
        } else {
            society = societyBrowseService.getStudentSocietyProfile(
                    societyID, authenticatedEmail);
        }

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Society profile retrieved successfully.",
                        society
                )
        );
    }

    /**
     * Returns the same public society content used by student profiles,
     * without membership state, management permission, or internal metrics.
     * This allows an authenticated profile manager to preview the public
     * profile without changing their role or receiving an executive DTO.
     */
    @GetMapping("/{societyID}/public")
    public ResponseEntity<ApiResponse<StudentSocietyProfileDTO>>
    getPublicSocietyProfile(@PathVariable String societyID) {
        StudentSocietyProfileDTO society =
                societyBrowseService.getPublicSocietyProfile(societyID);

        return ResponseEntity.ok(ApiResponse.success(
                "Public society profile retrieved successfully.",
                society));
    }

    /**
     * Returns every active, student-visible announcement associated with the
     * selected society. The payload contains no internal society fields.
     */
    @GetMapping("/{societyID}/announcements")
    public ResponseEntity<ApiResponse<List<SocietyAnnouncementSummaryDTO>>>
    getSocietyAnnouncements(@PathVariable String societyID, Authentication authentication) {
        List<SocietyAnnouncementSummaryDTO> announcements =
                societyBrowseService.getSocietyAnnouncements(societyID, authentication.getName());

        return ResponseEntity.ok(ApiResponse.success(
                "Society announcements retrieved successfully.",
                announcements));
    }

    /** Returns the full body for one active Society Highlight article. */
    @GetMapping("/{societyID}/highlights/{highlightID}")
    public ResponseEntity<ApiResponse<SocietyHighlightResponseDTO>>
    getSocietyHighlightArticle(
            @PathVariable String societyID,
            @PathVariable String highlightID) {
        SocietyHighlightResponseDTO article =
                societyBrowseService.getSocietyHighlightArticle(
                        societyID, highlightID);
        return ResponseEntity.ok(ApiResponse.success(
                "Society highlight retrieved successfully.",
                article));
    }

    private boolean hasRole(
            Authentication authentication,
            String role) {
        String expectedRole = "ROLE_" + role;
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equalsIgnoreCase(role)
                                || authority.getAuthority()
                                .equalsIgnoreCase(expectedRole));
    }
}
