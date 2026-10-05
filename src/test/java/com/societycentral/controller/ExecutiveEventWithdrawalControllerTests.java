package com.societycentral.controller;

import com.societycentral.config.SecurityConfig;
import com.societycentral.dto.response.EventResponseDTO;
import com.societycentral.exception.GlobalExceptionHandler;
import com.societycentral.model.EventStatus;
import com.societycentral.security.JwtAuthFilter;
import com.societycentral.security.JwtUtil;
import com.societycentral.service.EventService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutiveEventController.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        GlobalExceptionHandler.class,
        ExecutiveEventWithdrawalControllerTests.TestCorsConfiguration.class
})
class ExecutiveEventWithdrawalControllerTests {

    private static final String EVENT_ID = "EVT100";
    private static final String EXECUTIVE_EMAIL = "executive@nmu.ac.za";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void unauthenticatedWithdrawalIsRejected() throws Exception {
        mockMvc.perform(post(
                        "/api/executive/events/{eventID}/withdraw",
                        EVENT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("Authentication is required."));

        verifyNoInteractions(eventService);
    }

    @Test
    @WithMockUser(username = EXECUTIVE_EMAIL, roles = "STUDENT")
    void successfulWithdrawalReturnsStandardApiResponse() throws Exception {
        EventResponseDTO withdrawnEvent = EventResponseDTO.builder()
                .eventID(EVENT_ID)
                .eventStatus(EventStatus.WITHDRAWN)
                .build();
        when(eventService.withdrawEventProposal(
                EVENT_ID,
                EXECUTIVE_EMAIL)).thenReturn(withdrawnEvent);

        mockMvc.perform(post(
                        "/api/executive/events/{eventID}/withdraw",
                        EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value(
                        "Event proposal withdrawn successfully."))
                .andExpect(jsonPath("$.data.eventID").value(EVENT_ID))
                .andExpect(jsonPath("$.data.eventStatus")
                        .value("WITHDRAWN"));

        verify(eventService).withdrawEventProposal(
                EVENT_ID,
                EXECUTIVE_EMAIL);
    }

    @Test
    @WithMockUser(username = EXECUTIVE_EMAIL, roles = "STUDENT")
    void invalidWorkflowStatusReturnsConflict() throws Exception {
        when(eventService.withdrawEventProposal(
                EVENT_ID,
                EXECUTIVE_EMAIL))
                .thenThrow(new IllegalStateException(
                        "Only proposed events may be withdrawn."));

        mockMvc.perform(post(
                        "/api/executive/events/{eventID}/withdraw",
                        EVENT_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Only proposed events may be withdrawn."));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestCorsConfiguration {

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            return request -> null;
        }
    }
}
