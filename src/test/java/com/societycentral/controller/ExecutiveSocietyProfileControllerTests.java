package com.societycentral.controller;

import com.societycentral.dto.response.ExecutiveSocietyProfileDTO;
import com.societycentral.dto.response.SocietyGalleryMediaResponseDTO;
import com.societycentral.dto.response.SocietyHighlightResponseDTO;
import com.societycentral.dto.response.SocietyMediaUploadResponseDTO;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.exception.GlobalExceptionHandler;
import com.societycentral.model.SocietyImageType;
import com.societycentral.security.JwtUtil;
import com.societycentral.service.ExecutiveSocietyProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutiveSocietyProfileController.class)
@Import({
        GlobalExceptionHandler.class,
        ExecutiveSocietyProfileControllerTests.MethodSecurityConfiguration.class
})
class ExecutiveSocietyProfileControllerTests {

    private static final String EMAIL = "executive@nmu.ac.za";

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExecutiveSocietyProfileService profileService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = EMAIL, roles = "STUDENT")
    void activeExecutiveCanLoadOwnProfileAndCapability() throws Exception {
        when(profileService.getOwnProfile(EMAIL))
                .thenReturn(ExecutiveSocietyProfileDTO.builder()
                        .societyID("SOC001")
                        .societyName("Computing Society")
                        .canManageProfile(false)
                        .build());

        mockMvc.perform(get("/api/executive/society/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.societyID").value("SOC001"))
                .andExpect(jsonPath("$.data.canManageProfile").value(false));
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "STUDENT")
    void updateAcceptsOnlyPublicProfileContract() throws Exception {
        when(profileService.updatePublicProfile(eq(EMAIL), any()))
                .thenReturn(ExecutiveSocietyProfileDTO.builder()
                        .societyID("SOC001")
                        .description("Updated")
                        .canManageProfile(true)
                        .build());

        mockMvc.perform(put("/api/executive/society/profile")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Updated",
                                  "facebookURL": "https://facebook.com/computing"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Society profile updated successfully."))
                .andExpect(jsonPath("$.data.canManageProfile").value(true));
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "STUDENT")
    void invalidContactEmailIsRejectedBeforeService() throws Exception {
        mockMvc.perform(put("/api/executive/society/profile")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactEmail": "not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Contact email must be a valid email address."));

        verify(profileService, never())
                .updatePublicProfile(eq(EMAIL), any());
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "STUDENT")
    void studentWithoutExecutiveRoleReceivesForbidden() throws Exception {
        when(profileService.updatePublicProfile(eq(EMAIL), any()))
                .thenThrow(new ForbiddenOperationException(
                        "You are not authorised to edit this society profile."));

        mockMvc.perform(put("/api/executive/society/profile")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("You are not authorised to edit this society profile."));
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "STUDENT")
    void executiveCannotEditAnotherSocietyThroughScopedRoute()
            throws Exception {
        when(profileService.updatePublicProfile(
                eq(EMAIL), eq("SOC002"), any()))
                .thenThrow(new ForbiddenOperationException(
                        "You are not authorised to edit this society profile."));

        mockMvc.perform(put(
                        "/api/executive/societies/{societyID}/profile",
                        "SOC002")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Not mine\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("You are not authorised to edit this society profile."));

        verify(profileService).updatePublicProfile(
                eq(EMAIL), eq("SOC002"), any());
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "STUDENT")
    void logoAndBannerRoutesFixImageTypeWithoutClientParameter()
            throws Exception {
        SocietyMediaUploadResponseDTO logo = new SocietyMediaUploadResponseDTO(
                "/media/societies/logos/logo.png",
                "logo.png",
                SocietyImageType.LOGO,
                400,
                300,
                1024,
                Instant.parse("2030-08-11T08:30:00Z"));
        when(profileService.uploadSocietyLogo(eq(EMAIL), any()))
                .thenReturn(logo);
        when(profileService.uploadSocietyBanner(eq(EMAIL), any()))
                .thenReturn(new SocietyMediaUploadResponseDTO(
                        "/media/societies/banners/banner.png",
                        "banner.png",
                        SocietyImageType.BANNER,
                        1500,
                        500,
                        2048,
                        Instant.parse("2030-08-11T08:30:00Z")));

        mockMvc.perform(multipart(
                        "/api/executive/society/profile/logo")
                        .with(csrf())
                        .file("file", new byte[]{1}))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.imageType").value("LOGO"));

        mockMvc.perform(multipart(
                        "/api/executive/society/profile/banner")
                        .with(csrf())
                        .file("file", new byte[]{1}))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.imageType").value("BANNER"));
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "STUDENT")
    void scopedGalleryUploadReturnsPersistedSlide() throws Exception {
        SocietyGalleryMediaResponseDTO galleryMedia =
                SocietyGalleryMediaResponseDTO.builder()
                        .mediaID("MEDIA001")
                        .mediaUrl("/media/societies/gallery/image.png")
                        .caption("Outreach day")
                        .sortOrder(0)
                        .uploadedAt(LocalDateTime.of(
                                2030, 8, 11, 8, 30))
                        .build();
        when(profileService.uploadGalleryImage(
                eq(EMAIL), eq("SOC001"), any(), eq("Outreach day")))
                .thenReturn(galleryMedia);

        mockMvc.perform(multipart(
                        "/api/executive/societies/{societyID}/profile/gallery",
                        "SOC001")
                        .with(csrf())
                        .file("file", new byte[]{1})
                        .param("caption", "Outreach day"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mediaID").value("MEDIA001"))
                .andExpect(jsonPath("$.data.caption")
                        .value("Outreach day"));
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "STUDENT")
    void galleryReorderUsesDedicatedRouteNotCaptionRoute()
            throws Exception {
        when(profileService.reorderGallery(
                EMAIL, "SOC001", List.of("MEDIA002", "MEDIA001")))
                .thenReturn(List.of());

        mockMvc.perform(put(
                        "/api/executive/societies/{societyID}/profile/gallery/reorder",
                        "SOC001")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mediaIDs":["MEDIA002","MEDIA001"]}
                                """))
                .andExpect(status().isOk());

        verify(profileService).reorderGallery(
                EMAIL, "SOC001", List.of("MEDIA002", "MEDIA001"));
    }

    @Test
    @WithMockUser(username = EMAIL, roles = "STUDENT")
    void createsArticleHighlightFromJsonMetadataAndCoverImage()
            throws Exception {
        when(profileService.createHighlight(
                eq(EMAIL), eq("SOC001"), any(), any()))
                .thenReturn(SocietyHighlightResponseDTO.builder()
                        .highlightID("HIGHLIGHT001")
                        .headline("Computer Science Society Hosts Annual Hackathon")
                        .caption("Students spent 24 hours developing practical solutions.")
                        .article("Opening paragraph.\n\nClosing paragraph.")
                        .coverImageUrl(
                                "/media/societies/highlights/cover.png")
                        .activeStatus(true)
                        .build());

        var metadata = new org.springframework.mock.web.MockMultipartFile(
                "highlight",
                "highlight.json",
                MediaType.APPLICATION_JSON_VALUE,
                """
                        {
                          "headline":"Computer Science Society Hosts Annual Hackathon",
                          "caption":"Students spent 24 hours developing practical solutions.",
                          "article":"Opening paragraph.\\n\\nClosing paragraph.",
                          "category":"Technology",
                          "activeStatus":true
                        }
                        """.getBytes(StandardCharsets.UTF_8));
        var cover = new org.springframework.mock.web.MockMultipartFile(
                "coverImage",
                "cover.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2, 3});

        mockMvc.perform(multipart(
                        "/api/executive/societies/{societyID}/profile/highlights",
                        "SOC001")
                        .file(metadata)
                        .file(cover)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.highlightID")
                        .value("HIGHLIGHT001"))
                .andExpect(jsonPath("$.data.headline")
                        .value("Computer Science Society Hosts Annual Hackathon"))
                .andExpect(jsonPath("$.data.article")
                        .value("Opening paragraph.\n\nClosing paragraph."));

        verify(profileService).createHighlight(
                eq(EMAIL), eq("SOC001"), any(), any());
    }

    @Test
    @WithMockUser(username = "sdo@nmu.ac.za", roles = "SDO")
    void sdoCannotUseExecutiveManagementEndpoint() throws Exception {
        mockMvc.perform(get("/api/executive/society/profile"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(profileService);
    }
}
