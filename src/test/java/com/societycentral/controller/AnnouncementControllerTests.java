package com.societycentral.controller;

import com.societycentral.config.SecurityConfig;
import com.societycentral.exception.GlobalExceptionHandler;
import com.societycentral.model.Announcement;
import com.societycentral.model.TargetType;
import com.societycentral.security.JwtAuthFilter;
import com.societycentral.security.JwtUtil;
import com.societycentral.service.AnnouncementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnnouncementController.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        GlobalExceptionHandler.class,
        AnnouncementControllerTests.TestCorsConfiguration.class
})
class AnnouncementControllerTests {

    private static final String SENDER_EMAIL = "executive@nmu.ac.za";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnnouncementService announcementService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void rejectsUnauthenticatedCreation() throws Exception {
        mockMvc.perform(post("/api/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("Authentication is required."));

        verifyNoInteractions(announcementService);
    }

    @Test
    @WithMockUser(username = SENDER_EMAIL, roles = "STUDENT")
    void createsAnnouncementAndReturnsStandardResponse() throws Exception {
        LocalDateTime datePosted = LocalDateTime.of(2026, 7, 29, 12, 0);
        when(announcementService.create(any(Announcement.class)))
                .thenAnswer(invocation -> {
                    Announcement announcement = invocation.getArgument(0);
                    announcement.setAnnouncementID("ANN001");
                    announcement.setDatePosted(datePosted);
                    return announcement;
                });

        mockMvc.perform(post("/api/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("Announcement created successfully."))
                .andExpect(jsonPath("$.data.announcementID").value("ANN001"))
                .andExpect(jsonPath("$.data.subject").value("Committee meeting"))
                .andExpect(jsonPath("$.data.targetType").value("MEMBERS"))
                .andExpect(jsonPath("$.data.societyID").value("SOC001"))
                .andExpect(jsonPath("$.data.sentBy").value(SENDER_EMAIL));

        ArgumentCaptor<Announcement> captor =
                ArgumentCaptor.forClass(Announcement.class);
        verify(announcementService).create(captor.capture());

        Announcement submitted = captor.getValue();
        assertEquals("Committee meeting", submitted.getSubject());
        assertEquals(TargetType.MEMBERS, submitted.getTargetType());
        assertEquals("SOC001", submitted.getSociety().getSocietyID());
        assertEquals(SENDER_EMAIL, submitted.getSentBy());
    }

    @Test
    @WithMockUser(username = SENDER_EMAIL, roles = "STUDENT")
    void rejectsMissingSubjectBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Agenda",
                                  "targetType": "MEMBERS",
                                  "expireDate": "2099-08-15T12:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.data.subject")
                        .value("Announcement subject is required."));

        verifyNoInteractions(announcementService);
    }

    @Test
    @WithMockUser(username = SENDER_EMAIL, roles = "STUDENT")
    void rejectsPastExpiryBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subject": "Committee meeting",
                                  "targetType": "MEMBERS",
                                  "expireDate": "2000-01-01T12:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.data.expireDate")
                        .value("Announcement expiry date must be in the future."));

        verifyNoInteractions(announcementService);
    }

    private static String validRequest() {
        return """
                {
                  "societyID": "  SOC001  ",
                  "subject": "  Committee meeting  ",
                  "description": "Agenda",
                  "targetType": "MEMBERS",
                  "expireDate": "2099-08-15T12:00:00"
                }
                """;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestCorsConfiguration {

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            return request -> null;
        }
    }
}
