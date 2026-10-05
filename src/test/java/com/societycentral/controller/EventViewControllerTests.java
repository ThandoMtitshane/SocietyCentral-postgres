package com.societycentral.controller;

import com.societycentral.dto.response.EventResponseDTO;
import com.societycentral.dto.response.StudentEventSummaryDTO;
import com.societycentral.exception.GlobalExceptionHandler;
import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.model.EventStatus;
import com.societycentral.security.JwtUtil;
import com.societycentral.service.EventViewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventViewController.class)
@Import(GlobalExceptionHandler.class)
class EventViewControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventViewService eventViewService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = "student@nmu.ac.za", roles = "STUDENT")
    void studentListUsesRestrictedVisibilityService() throws Exception {
        when(eventViewService.getVisibleEventsForStudent(
                "student@nmu.ac.za"))
                .thenReturn(List.of(new StudentEventSummaryDTO()));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));

        verify(eventViewService).getVisibleEventsForStudent(
                "student@nmu.ac.za");
        verify(eventViewService, never()).getAllPublishedEvents();
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void studentAuthorityDelegatesExecutiveResolutionToService()
            throws Exception {
        when(eventViewService.getVisibleEventsForStudent(
                "executive@nmu.ac.za"))
                .thenReturn(List.of(new StudentEventSummaryDTO()));

        mockMvc.perform(get("/api/events/visible"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));

        verify(eventViewService).getVisibleEventsForStudent(
                "executive@nmu.ac.za");
        verify(eventViewService, never()).getAllPublishedEvents();
    }

    @Test
    @WithMockUser(username = "sdo@nmu.ac.za", roles = "SDO")
    void sdoListUsesAllPublishedEvents() throws Exception {
        when(eventViewService.getAllPublishedEvents())
                .thenReturn(List.of(new StudentEventSummaryDTO()));

        mockMvc.perform(get("/api/events/visible"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"));

        verify(eventViewService).getAllPublishedEvents();
        verify(eventViewService, never()).getVisibleEventsForStudent(
                "sdo@nmu.ac.za");
    }

    @Test
    @WithMockUser(username = "student@nmu.ac.za", roles = "STUDENT")
    void studentDetailUsesStudentVisibilityService() throws Exception {
        when(eventViewService.getVisibleEventForStudent(
                "EVT100",
                "student@nmu.ac.za"))
                .thenReturn(EventResponseDTO.builder()
                        .eventID("EVT100")
                        .eventName("Welcome Evening")
                        .eventStatus(EventStatus.PUBLISHED)
                        .build());

        mockMvc.perform(get("/api/events/EVT100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value(
                        "Event retrieved successfully"))
                .andExpect(jsonPath("$.data.eventID").value("EVT100"))
                .andExpect(jsonPath("$.data.eventStatus").value("PUBLISHED"));

        verify(eventViewService).getVisibleEventForStudent(
                "EVT100",
                "student@nmu.ac.za");
        verify(eventViewService, never()).getPublishedEvent("EVT100");
    }

    @Test
    @WithMockUser(username = "sdo@nmu.ac.za", roles = "SDO")
    void sdoDetailUsesPublishedEventService() throws Exception {
        when(eventViewService.getPublishedEvent("EVT200"))
                .thenReturn(EventResponseDTO.builder()
                        .eventID("EVT200")
                        .eventStatus(EventStatus.PUBLISHED)
                        .build());

        mockMvc.perform(get("/api/events/EVT200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventID").value("EVT200"));

        verify(eventViewService).getPublishedEvent("EVT200");
        verify(eventViewService, never()).getVisibleEventForStudent(
                "EVT200",
                "sdo@nmu.ac.za");
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void studentAuthorityDelegatesExecutiveDetailResolutionToService()
            throws Exception {
        when(eventViewService.getVisibleEventForStudent(
                "EVT300",
                "executive@nmu.ac.za"))
                .thenReturn(EventResponseDTO.builder()
                        .eventID("EVT300")
                        .eventStatus(EventStatus.PUBLISHED)
                        .build());

        mockMvc.perform(get("/api/events/EVT300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventID").value("EVT300"));

        verify(eventViewService).getVisibleEventForStudent(
                "EVT300",
                "executive@nmu.ac.za");
        verify(eventViewService, never()).getPublishedEvent("EVT300");
    }

    @Test
    @WithMockUser(username = "student@nmu.ac.za", roles = "STUDENT")
    void invisibleEventIDReturnsNotFoundWithoutDetails() throws Exception {
        when(eventViewService.getVisibleEventForStudent(
                "EVT_PRIVATE",
                "student@nmu.ac.za"))
                .thenThrow(new ResourceNotFoundException(
                        "Event not found."));

        mockMvc.perform(get("/api/events/EVT_PRIVATE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message").value("Event not found."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
