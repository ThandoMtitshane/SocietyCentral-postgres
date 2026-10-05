package com.societycentral.controller;

import com.societycentral.dto.request.CreateEventRequestDTO;
import com.societycentral.dto.response.EventResponseDTO;
import com.societycentral.dto.response.ExecutiveEventPageDTO;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.exception.GlobalExceptionHandler;
import com.societycentral.exception.VenueUnavailableException;
import com.societycentral.model.Campus;
import com.societycentral.model.EventStatus;
import com.societycentral.security.JwtUtil;
import com.societycentral.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutiveEventController.class)
@Import(GlobalExceptionHandler.class)
class ExecutiveEventControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void listAcceptsStatusPaginationAndSearchWithoutSocietyId() throws Exception {
        ExecutiveEventPageDTO page = ExecutiveEventPageDTO.builder()
                .content(List.of())
                .page(1)
                .size(10)
                .totalElements(0)
                .totalPages(0)
                .first(false)
                .last(true)
                .empty(true)
                .build();
        when(eventService.getExecutiveEvents(
                "executive@nmu.ac.za",
                EventStatus.REJECTED,
                1,
                10,
                "venue"))
                .thenReturn(page);

        mockMvc.perform(get("/api/executive/events")
                        .param("status", "REJECTED")
                        .param("page", "1")
                        .param("size", "10")
                        .param("search", "venue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.empty").value(true));

        verify(eventService).getExecutiveEvents(
                "executive@nmu.ac.za",
                EventStatus.REJECTED,
                1,
                10,
                "venue");
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void anotherSocietyEventDetailReturnsHttp403() throws Exception {
        when(eventService.getExecutiveEvent(
                "EVT200",
                "executive@nmu.ac.za"))
                .thenThrow(new ForbiddenOperationException(
                        "The event does not belong to your society."));

        mockMvc.perform(get("/api/executive/events/EVT200"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "The event does not belong to your society."));
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void eventDetailsReturnCompleteAuthoritativeVenueSelection() throws Exception {
        when(eventService.getExecutiveEvent(
                "EVT100",
                "executive@nmu.ac.za"))
                .thenReturn(EventResponseDTO.builder()
                        .eventID("EVT100")
                        .venueCode("SC001")
                        .venueName("Auditorium")
                        .venueType("Auditorium")
                        .venueCapacity(850)
                        .eventCampus(Campus.SOUTH_CAMPUS)
                        .campus(Campus.SOUTH_CAMPUS)
                        .build());

        mockMvc.perform(get("/api/executive/events/EVT100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.venueCode").value("SC001"))
                .andExpect(jsonPath("$.data.venueName").value("Auditorium"))
                .andExpect(jsonPath("$.data.venueType").value("Auditorium"))
                .andExpect(jsonPath("$.data.venueCapacity").value(850))
                .andExpect(jsonPath("$.data.campus").value("SOUTH_CAMPUS"));
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void successfulPutReturnsUpdatedSameEvent() throws Exception {
        when(eventService.updateExecutiveEvent(
                eq("EVT100"),
                eq("executive@nmu.ac.za"),
                any(CreateEventRequestDTO.class)))
                .thenReturn(EventResponseDTO.builder()
                        .eventID("EVT100")
                        .eventStatus(EventStatus.DRAFT)
                        .build());

        mockMvc.perform(put("/api/executive/events/EVT100")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.data.eventID").value("EVT100"))
                .andExpect(jsonPath("$.data.eventStatus").value("DRAFT"));
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void proposedEventPutReturnsHttp409() throws Exception {
        when(eventService.updateExecutiveEvent(
                eq("EVT100"),
                eq("executive@nmu.ac.za"),
                any(CreateEventRequestDTO.class)))
                .thenThrow(new IllegalStateException(
                        "Only draft or rejected events may be edited."));

        mockMvc.perform(put("/api/executive/events/EVT100")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Only draft or rejected events may be edited."));
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void missingVenueSelectionOnUpdateReturnsHttp400() throws Exception {
        String requestWithoutVenueCode = validRequestJson(true)
                .replace("\"venueCode\": \"SC001\",", "");

        mockMvc.perform(put("/api/executive/events/EVT100")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestWithoutVenueCode))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Venue selection is required."));

        verifyNoInteractions(eventService);
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void removedVenueOnUpdateReturnsHttp409() throws Exception {
        when(eventService.updateExecutiveEvent(
                eq("EVT100"),
                eq("executive@nmu.ac.za"),
                any(CreateEventRequestDTO.class)))
                .thenThrow(new VenueUnavailableException(
                        "The selected venue is no longer available."));

        mockMvc.perform(put("/api/executive/events/EVT100")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("The selected venue is no longer available."));
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void anotherEventVenueConflictOnUpdateReturnsHttp409() throws Exception {
        when(eventService.updateExecutiveEvent(
                eq("EVT100"),
                eq("executive@nmu.ac.za"),
                any(CreateEventRequestDTO.class)))
                .thenThrow(new VenueUnavailableException(
                        "The selected venue is already booked for this date and time."));

        mockMvc.perform(put("/api/executive/events/EVT100")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "The selected venue is already booked for this date and time."));
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void venueConflictReturnsHttp409() throws Exception {
        when(eventService.createEvent(
                eq("SOC001"),
                eq("executive@nmu.ac.za"),
                any(CreateEventRequestDTO.class)))
                .thenThrow(new VenueUnavailableException());

        mockMvc.perform(post("/api/executive/societies/SOC001/events")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "The selected venue is not available for the requested date and time."));
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void missingEndTimeReturnsBadRequestBeforeService() throws Exception {
        mockMvc.perform(post("/api/executive/societies/SOC001/events")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Event end time is required."));

        verifyNoInteractions(eventService);
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void reversedEventTimesReturnBusinessValidationResponse() throws Exception {
        when(eventService.createEvent(
                eq("SOC001"),
                eq("executive@nmu.ac.za"),
                any(CreateEventRequestDTO.class)))
                .thenThrow(new IllegalArgumentException(
                        "Event end time must be after the start time."));

        mockMvc.perform(post("/api/executive/societies/SOC001/events")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("23:00", "22:30")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Event end time must be after the start time."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private String validRequestJson(boolean includeEndTime) {
        return requestJson(
                "10:00",
                includeEndTime ? "12:00" : null);
    }

    private String requestJson(
            String startTime,
            String endTime) {
        String endTimeField = endTime == null
                ? ""
                : "\"eventEndTime\":\"" + endTime + "\",";
        return """
                {
                  "eventName": "Welcome Event",
                  "eventDate": "2030-08-10",
                  "eventStartTime": "%s",
                  %s
                  "venueCode": "SC001",
                  "eventCampus": "SOUTH_CAMPUS",
                  "eventDescription": "Welcome event for society members.",
                  "eventLimit": 100,
                  "attendingType": "MEMBERS",
                  "posterUrl": "http://localhost:8080/media/events/posters/poster.png",
                  "bannerUrl": "http://localhost:8080/media/events/banners/banner.png"
                }
                """.formatted(startTime, endTimeField);
    }
}
