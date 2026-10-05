package com.societycentral.controller;

import com.societycentral.config.SecurityConfig;
import com.societycentral.dto.response.ResponseType;
import com.societycentral.exception.GlobalExceptionHandler;
import com.societycentral.model.EventStatus;
import com.societycentral.security.JwtAuthFilter;
import com.societycentral.security.JwtUtil;
import com.societycentral.service.EventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutiveEventController.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        GlobalExceptionHandler.class,
        ExecutiveEventDeletionControllerTests.TestCorsConfiguration.class
})
class ExecutiveEventDeletionControllerTests {

    private static final String EVENT_ID = "EVT100";
    private static final String EXECUTIVE_EMAIL =
            "executive@nmu.ac.za";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void endpointRequiresAuthentication() throws Exception {
        mockMvc.perform(delete(
                        "/api/executive/events/{eventID}",
                        EVENT_ID
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type")
                        .value(ResponseType.ERROR.name()))
                .andExpect(jsonPath("$.message")
                        .value("Authentication is required."))
                .andExpect(jsonPath("$.data")
                        .value(nullValue()));

        verifyNoInteractions(eventService);
    }

    @Test
    @WithMockUser(
            username = EXECUTIVE_EMAIL,
            roles = "STUDENT"
    )
    void ownedDraftReturnsTheStandardSuccessResponse()
            throws Exception {

        mockMvc.perform(delete(
                        "/api/executive/events/{eventID}",
                        EVENT_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type")
                        .value(ResponseType.SUCCESS.name()))
                .andExpect(jsonPath("$.message")
                        .value("Draft deleted successfully."))
                .andExpect(jsonPath("$.data")
                        .value(nullValue()));

        verify(eventService).deleteDraftEvent(
                EVENT_ID,
                EXECUTIVE_EMAIL
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = EventStatus.class,
            names = {"PROPOSED", "APPROVED"}
    )
    @WithMockUser(
            username = EXECUTIVE_EMAIL,
            roles = "STUDENT"
    )
    void proposedAndApprovedEventsReturnConflict(
            EventStatus status) throws Exception {

        String eventID = "EVT_" + status.name();
        doThrow(new IllegalStateException(
                "Only draft events may be deleted."
        )).when(eventService).deleteDraftEvent(
                eventID,
                EXECUTIVE_EMAIL
        );

        mockMvc.perform(delete(
                        "/api/executive/events/{eventID}",
                        eventID
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value(ResponseType.ERROR.name()))
                .andExpect(jsonPath("$.message")
                        .value("Only draft events may be deleted."))
                .andExpect(jsonPath("$.data")
                        .value(nullValue()));
    }

    @Test
    @WithMockUser(
            username = EXECUTIVE_EMAIL,
            roles = "STUDENT"
    )
    void anotherSocietyReturnsBadRequest() throws Exception {
        doThrow(new IllegalArgumentException(
                "You are not authorised to delete this draft."
        )).when(eventService).deleteDraftEvent(
                EVENT_ID,
                EXECUTIVE_EMAIL
        );

        mockMvc.perform(delete(
                        "/api/executive/events/{eventID}",
                        EVENT_ID
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value(ResponseType.ERROR.name()))
                .andExpect(jsonPath("$.message").value(
                        "You are not authorised to delete this draft."
                ));
    }

    @Test
    @WithMockUser(
            username = EXECUTIVE_EMAIL,
            roles = "STUDENT"
    )
    void missingEventReturnsBadRequest() throws Exception {
        doThrow(new IllegalArgumentException(
                "Event not found."
        )).when(eventService).deleteDraftEvent(
                EVENT_ID,
                EXECUTIVE_EMAIL
        );

        mockMvc.perform(delete(
                        "/api/executive/events/{eventID}",
                        EVENT_ID
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value(ResponseType.ERROR.name()))
                .andExpect(jsonPath("$.message")
                        .value("Event not found."));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestCorsConfiguration {

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            return request -> null;
        }
    }
}
