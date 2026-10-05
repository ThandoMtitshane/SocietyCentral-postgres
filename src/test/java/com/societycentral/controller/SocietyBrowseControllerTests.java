package com.societycentral.controller;

import com.societycentral.dto.response.ExecutiveSocietyProfileDTO;
import com.societycentral.dto.response.MembershipState;
import com.societycentral.dto.response.SDOSocietyProfileDTO;
import com.societycentral.dto.response.SocietyAnnouncementSummaryDTO;
import com.societycentral.dto.response.SocietyBrowseSummaryResponseDTO;
import com.societycentral.dto.response.SocietyExecutiveSummaryDTO;
import com.societycentral.dto.response.SocietyGalleryMediaResponseDTO;
import com.societycentral.dto.response.SocietyHighlightResponseDTO;
import com.societycentral.dto.response.StudentSocietyProfileDTO;
import com.societycentral.exception.GlobalExceptionHandler;
import com.societycentral.security.JwtUtil;
import com.societycentral.service.SocietyBrowseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SocietyBrowseController.class)
@Import(GlobalExceptionHandler.class)
class SocietyBrowseControllerTests {

    private static final String SOCIETY_ID = "SOC001";
    private static final String STUDENT_EMAIL = "student@nmu.ac.za";
    private static final String EXECUTIVE_EMAIL = "executive@nmu.ac.za";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SocietyBrowseService societyBrowseService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = "STUDENT")
    void browseListContainsOnlyPublicCardFields() throws Exception {
        when(societyBrowseService.getActiveSocieties())
                .thenReturn(List.of(
                        SocietyBrowseSummaryResponseDTO.builder()
                                .societyID(SOCIETY_ID)
                                .societyName("Computing Society")
                                .build()));

        mockMvc.perform(get("/api/societies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].societyID")
                        .value(SOCIETY_ID))
                .andExpect(jsonPath("$.data[0].numberOfMembers")
                        .doesNotExist())
                .andExpect(jsonPath("$.data[0].currentBalance")
                        .doesNotExist())
                .andExpect(jsonPath("$.data[0].annualBudgetAllocation")
                        .doesNotExist());
    }

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = "STUDENT")
    void studentProfileNeverSerialisesInternalMetrics() throws Exception {
        StudentSocietyProfileDTO profile = StudentSocietyProfileDTO.builder()
                .societyName("Computing Society")
                .facebookURL("https://facebook.com/computing")
                .membershipState(MembershipState.ELIGIBLE)
                .executives(List.of(
                        SocietyExecutiveSummaryDTO.builder()
                                .fullName("Alex Smith")
                                .position("President")
                                .build()))
                .upcomingEvents(List.of())
                .announcements(List.of())
                .build();
        when(societyBrowseService.isActiveExecutiveOfSociety(
                STUDENT_EMAIL, SOCIETY_ID)).thenReturn(false);
        when(societyBrowseService.getStudentSocietyProfile(
                SOCIETY_ID, STUDENT_EMAIL)).thenReturn(profile);

        mockMvc.perform(get("/api/societies/{societyID}", SOCIETY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.facebookURL")
                        .value("https://facebook.com/computing"))
                .andExpect(jsonPath("$.data.membershipState")
                        .value("ELIGIBLE"))
                .andExpect(jsonPath("$.data.executives[0].fullName")
                        .value("Alex Smith"))
                .andExpect(jsonPath("$.data.societyID").doesNotExist())
                .andExpect(jsonPath("$.data.numberOfMembers")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.currentBalance")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.annualBudgetAllocation")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.flagged").doesNotExist())
                .andExpect(jsonPath("$.data.atRisk").doesNotExist())
                .andExpect(jsonPath("$.data.pendingTasks")
                        .doesNotExist());
    }

    @Test
    @WithMockUser(username = EXECUTIVE_EMAIL, roles = "STUDENT")
    void executiveReceivesPrivateDataForOwnSociety() throws Exception {
        when(societyBrowseService.isActiveExecutiveOfSociety(
                EXECUTIVE_EMAIL, SOCIETY_ID)).thenReturn(true);
        when(societyBrowseService.getExecutiveSocietyProfile(
                SOCIETY_ID, EXECUTIVE_EMAIL))
                .thenReturn(ExecutiveSocietyProfileDTO.builder()
                        .societyID(SOCIETY_ID)
                        .numberOfMembers(72)
                        .currentBalance(new BigDecimal("12000.00"))
                        .annualBudgetAllocation(
                                new BigDecimal("20000.00"))
                        .canManageProfile(true)
                        .canViewInternalData(true)
                        .build());

        mockMvc.perform(get("/api/societies/{societyID}", SOCIETY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.societyID")
                        .value(SOCIETY_ID))
                .andExpect(jsonPath("$.data.numberOfMembers").value(72))
                .andExpect(jsonPath("$.data.currentBalance")
                        .value(12000.00))
                .andExpect(jsonPath("$.data.annualBudgetAllocation")
                        .value(20000.00))
                .andExpect(jsonPath("$.data.canManageProfile")
                        .value(true))
                .andExpect(jsonPath("$.data.canEditProfile")
                        .value(true));

        verify(societyBrowseService, never())
                .getStudentSocietyProfile(SOCIETY_ID, EXECUTIVE_EMAIL);
    }

    @Test
    @WithMockUser(username = EXECUTIVE_EMAIL, roles = "STUDENT")
    void publicPreviewReturnsMediaAndNeverReturnsInternalMetrics()
            throws Exception {
        StudentSocietyProfileDTO publicProfile =
                StudentSocietyProfileDTO.builder()
                        .societyName("Computing Society")
                        .logoUrl("/media/societies/logos/logo.png")
                        .bannerUrl("/media/societies/banners/banner.png")
                        .gallery(List.of(
                                SocietyGalleryMediaResponseDTO.builder()
                                        .mediaID("MEDIA001")
                                        .mediaUrl(
                                                "/media/societies/gallery/one.png")
                                        .caption("Outreach day")
                                        .sortOrder(0)
                                        .build()))
                        .build();
        when(societyBrowseService.getPublicSocietyProfile(SOCIETY_ID))
                .thenReturn(publicProfile);

        mockMvc.perform(get(
                        "/api/societies/{societyID}/public",
                        SOCIETY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logoUrl")
                        .value("/media/societies/logos/logo.png"))
                .andExpect(jsonPath("$.data.bannerUrl")
                        .value("/media/societies/banners/banner.png"))
                .andExpect(jsonPath("$.data.gallery[0].mediaID")
                        .value("MEDIA001"))
                .andExpect(jsonPath("$.data.gallery[0].sortOrder")
                        .value(0))
                .andExpect(jsonPath("$.data.numberOfMembers")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.currentBalance")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.annualBudgetAllocation")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.canEditProfile")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.canManageProfile")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.canViewInternalData")
                        .doesNotExist());

        verify(societyBrowseService)
                .getPublicSocietyProfile(SOCIETY_ID);
        verify(societyBrowseService, never())
                .isActiveExecutiveOfSociety(
                        EXECUTIVE_EMAIL, SOCIETY_ID);
    }

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = "STUDENT")
    void articleEndpointReturnsTheFullPublishedHighlightBody()
            throws Exception {
        when(societyBrowseService.getSocietyHighlightArticle(
                SOCIETY_ID, "HIGHLIGHT001"))
                .thenReturn(SocietyHighlightResponseDTO.builder()
                        .highlightID("HIGHLIGHT001")
                        .societyName("Computing Society")
                        .headline("Annual Hackathon")
                        .caption("Students built practical solutions.")
                        .article("First paragraph.\n\nSecond paragraph.")
                        .coverImageUrl(
                                "/media/societies/highlights/cover.png")
                        .publishedAt(LocalDateTime.of(
                                2030, 8, 11, 8, 30))
                        .activeStatus(true)
                        .build());

        mockMvc.perform(get(
                        "/api/societies/{societyID}/highlights/{highlightID}",
                        SOCIETY_ID,
                        "HIGHLIGHT001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.societyName")
                        .value("Computing Society"))
                .andExpect(jsonPath("$.data.headline")
                        .value("Annual Hackathon"))
                .andExpect(jsonPath("$.data.article")
                        .value("First paragraph.\n\nSecond paragraph."));
    }

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = "STUDENT")
    void allSocietyAnnouncementsUseThePublicSafeReader() throws Exception {
        when(societyBrowseService.getSocietyAnnouncements(
                SOCIETY_ID, STUDENT_EMAIL))
                .thenReturn(List.of(
                        SocietyAnnouncementSummaryDTO.builder()
                                .announcementID("ANN001")
                                .subject("Applications open")
                                .description("Join us this semester.")
                                .datePosted(LocalDateTime.of(
                                        2030, 8, 11, 8, 30))
                                .build()));

        mockMvc.perform(get(
                        "/api/societies/{societyID}/announcements",
                        SOCIETY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].announcementID")
                        .value("ANN001"))
                .andExpect(jsonPath("$.data[0].subject")
                        .value("Applications open"))
                .andExpect(jsonPath("$.data[0].currentBalance")
                        .doesNotExist());

        verify(societyBrowseService)
                .getSocietyAnnouncements(SOCIETY_ID, STUDENT_EMAIL);
    }

    @Test
    @WithMockUser(username = EXECUTIVE_EMAIL, roles = "STUDENT")
    void executiveReceivesPublicProfileForAnotherSociety()
            throws Exception {
        String otherSocietyID = "SOC002";
        when(societyBrowseService.isActiveExecutiveOfSociety(
                EXECUTIVE_EMAIL, otherSocietyID)).thenReturn(false);
        when(societyBrowseService.getStudentSocietyProfile(
                otherSocietyID, EXECUTIVE_EMAIL))
                .thenReturn(StudentSocietyProfileDTO.builder()
                        .societyName("Robotics Society")
                        .build());

        mockMvc.perform(get("/api/societies/{societyID}", otherSocietyID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.societyName")
                        .value("Robotics Society"))
                .andExpect(jsonPath("$.data.societyID").doesNotExist())
                .andExpect(jsonPath("$.data.numberOfMembers")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.currentBalance")
                        .doesNotExist());

        verify(societyBrowseService, never())
                .getExecutiveSocietyProfile(
                        otherSocietyID, EXECUTIVE_EMAIL);
        verify(societyBrowseService)
                .getStudentSocietyProfile(
                        otherSocietyID, EXECUTIVE_EMAIL);
    }

    @Test
    @WithMockUser(
            username = "sdo@nmu.ac.za",
            authorities = {"ROLE_STUDENT", "role_sdo"})
    void sdoAuthorityIsResolvedAcrossAllAuthoritiesCaseInsensitively()
            throws Exception {
        when(societyBrowseService.getSDOSocietyProfile(SOCIETY_ID))
                .thenReturn(SDOSocietyProfileDTO.builder()
                        .societyID(SOCIETY_ID)
                        .activeStatus(false)
                        .numberOfMembers(41)
                        .flagged(true)
                        .build());

        mockMvc.perform(get("/api/societies/{societyID}", SOCIETY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.societyID")
                        .value(SOCIETY_ID))
                .andExpect(jsonPath("$.data.activeStatus").value(false))
                .andExpect(jsonPath("$.data.numberOfMembers").value(41))
                .andExpect(jsonPath("$.data.flagged").value(true));

        verify(societyBrowseService, never())
                .isActiveExecutiveOfSociety(
                        "sdo@nmu.ac.za", SOCIETY_ID);
    }
}
