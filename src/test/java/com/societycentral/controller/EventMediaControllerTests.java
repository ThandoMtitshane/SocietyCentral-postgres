package com.societycentral.controller;

import com.societycentral.config.SecurityConfig;
import com.societycentral.dto.response.EventMediaUploadResponseDTO;
import com.societycentral.exception.EventMediaException;
import com.societycentral.exception.GlobalExceptionHandler;
import com.societycentral.model.EventImageType;
import com.societycentral.security.JwtAuthFilter;
import com.societycentral.security.JwtUtil;
import com.societycentral.service.EventMediaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventMediaController.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        GlobalExceptionHandler.class,
        EventMediaControllerTests.TestCorsConfiguration.class
})
class EventMediaControllerTests {

    private static final String EXECUTIVE_EMAIL = "executive@nmu.ac.za";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventMediaService eventMediaService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void rejectsUnauthenticatedUpload() throws Exception {
        mockMvc.perform(multipart("/api/executive/events/media")
                        .file("file", new byte[]{1})
                        .param("imageType", "POSTER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("Authentication is required."));

        verifyNoInteractions(eventMediaService);
    }

    @Test
    @WithMockUser(username = EXECUTIVE_EMAIL, roles = "STUDENT")
    void rejectsAuthenticatedStudentWithoutExecutiveRole() throws Exception {
        when(eventMediaService.upload(
                eq(EXECUTIVE_EMAIL),
                any(),
                eq(EventImageType.POSTER)))
                .thenThrow(EventMediaException.unauthorisedExecutive());

        mockMvc.perform(multipart("/api/executive/events/media")
                        .file("file", new byte[]{1})
                        .param("imageType", "POSTER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Only an active society executive may upload event media."));
    }

    @Test
    @WithMockUser(username = "sdo@nmu.ac.za", roles = "SDO")
    void rejectsNonStudentRoleBeforeUploadService() throws Exception {
        mockMvc.perform(multipart("/api/executive/events/media")
                        .file("file", new byte[]{1})
                        .param("imageType", "POSTER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("ERROR"));

        verifyNoInteractions(eventMediaService);
    }

    @Test
    @WithMockUser(username = EXECUTIVE_EMAIL, roles = "STUDENT")
    void returnsApiResponseForSuccessfulUpload() throws Exception {
        EventMediaUploadResponseDTO uploaded =
                new EventMediaUploadResponseDTO(
                        "http://localhost:8080/media/events/posters/"
                                + "28f1a565-4664-4bc8-9ceb-4b2e1a53f2dd.png",
                        "28f1a565-4664-4bc8-9ceb-4b2e1a53f2dd.png",
                        EventImageType.POSTER,
                        1080,
                        1350,
                        123456L,
                        Instant.parse("2026-07-23T00:00:00Z"));
        when(eventMediaService.upload(
                eq(EXECUTIVE_EMAIL),
                any(),
                eq(EventImageType.POSTER))).thenReturn(uploaded);

        mockMvc.perform(multipart("/api/executive/events/media")
                        .file("file", new byte[]{1})
                        .param("imageType", "POSTER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.data.imageType").value("POSTER"))
                .andExpect(jsonPath("$.data.width").value(1080))
                .andExpect(jsonPath("$.data.height").value(1350))
                .andExpect(jsonPath("$.data.sizeBytes").value(123456));
    }

    @Test
    @WithMockUser(username = EXECUTIVE_EMAIL, roles = "STUDENT")
    void staleImagesPathReturnsApiNotFoundWithoutInvokingUploadService()
            throws Exception {
        mockMvc.perform(multipart("/api/executive/events/images")
                        .file("file", new byte[]{1})
                        .param("imageType", "POSTER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message").value("Resource not found."));

        verifyNoInteractions(eventMediaService);
    }

    @Test
    @WithMockUser(username = EXECUTIVE_EMAIL, roles = "STUDENT")
    void rejectsInvalidImageTypeAtCanonicalPath() throws Exception {
        mockMvc.perform(multipart("/api/executive/events/media")
                        .file("file", new byte[]{1})
                        .param("imageType", "THUMBNAIL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("imageType must be POSTER or BANNER."));

        verifyNoInteractions(eventMediaService);
    }

    @Test
    @WithMockUser(username = EXECUTIVE_EMAIL, roles = "STUDENT")
    void preservesMissingImageTypeValidationMessage() throws Exception {
        mockMvc.perform(multipart("/api/executive/events/media")
                        .file("file", new byte[]{1}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Multipart fields 'file' and 'imageType' are required."));

        verifyNoInteractions(eventMediaService);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestCorsConfiguration {

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            return request -> null;
        }
    }
}
