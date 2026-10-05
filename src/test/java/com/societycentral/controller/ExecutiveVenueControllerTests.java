package com.societycentral.controller;

import com.societycentral.dto.response.VenueAvailabilityDTO;
import com.societycentral.exception.GlobalExceptionHandler;
import com.societycentral.model.Campus;
import com.societycentral.security.JwtUtil;
import com.societycentral.service.VenueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutiveVenueController.class)
@Import(GlobalExceptionHandler.class)
class ExecutiveVenueControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VenueService venueService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void returnsAvailableVenues() throws Exception {
        LocalDate eventDate = LocalDate.of(2026, 8, 10);
        when(venueService.getAvailableVenues(
                Campus.SOUTH_CAMPUS,
                eventDate,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                null))
                .thenReturn(List.of(VenueAvailabilityDTO.builder()
                        .venueCode("SC001")
                        .campus(Campus.SOUTH_CAMPUS)
                        .venueName("Auditorium")
                        .venueType("Auditorium")
                        .capacity(850)
                        .build()));

        mockMvc.perform(get("/api/executive/venues/available")
                        .param("campus", "SOUTH_CAMPUS")
                        .param("eventDate", "2026-08-10")
                        .param("startTime", "10:00")
                        .param("endTime", "12:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].venueCode").value("SC001"))
                .andExpect(jsonPath("$.data[0].capacity").value(850));
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void missingRequiredAvailabilityParameterReturnsBadRequest()
            throws Exception {
        when(venueService.getAvailableVenues(
                Campus.SOUTH_CAMPUS,
                LocalDate.of(2026, 8, 10),
                LocalTime.of(10, 0),
                null,
                null))
                .thenThrow(new IllegalArgumentException(
                        "Event end time is required."));

        mockMvc.perform(get("/api/executive/venues/available")
                        .param("campus", "SOUTH_CAMPUS")
                        .param("eventDate", "2026-08-10")
                        .param("startTime", "10:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Event end time is required."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void reversedAvailabilityTimesReturnBusinessValidationResponse()
            throws Exception {
        LocalDate eventDate = LocalDate.of(2026, 8, 10);
        LocalTime startTime = LocalTime.of(23, 0);
        LocalTime endTime = LocalTime.of(22, 30);
        when(venueService.getAvailableVenues(
                Campus.SOUTH_CAMPUS,
                eventDate,
                startTime,
                endTime,
                null))
                .thenThrow(new IllegalArgumentException(
                        "Event end time must be after the start time."));

        mockMvc.perform(get("/api/executive/venues/available")
                        .param("campus", "SOUTH_CAMPUS")
                        .param("eventDate", "2026-08-10")
                        .param("startTime", "23:00")
                        .param("endTime", "22:30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Event end time must be after the start time."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void equalAvailabilityTimesReturnBusinessValidationResponse()
            throws Exception {
        LocalDate eventDate = LocalDate.of(2026, 8, 10);
        LocalTime time = LocalTime.of(10, 0);
        when(venueService.getAvailableVenues(
                Campus.SOUTH_CAMPUS,
                eventDate,
                time,
                time,
                null))
                .thenThrow(new IllegalArgumentException(
                        "Event end time must be after the start time."));

        mockMvc.perform(get("/api/executive/venues/available")
                        .param("campus", "SOUTH_CAMPUS")
                        .param("eventDate", "2026-08-10")
                        .param("startTime", "10:00")
                        .param("endTime", "10:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Event end time must be after the start time."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void editAvailabilityForwardsExcludedEventIdentifier() throws Exception {
        LocalDate eventDate = LocalDate.of(2026, 8, 10);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(12, 0);
        when(venueService.getAvailableVenues(
                Campus.SOUTH_CAMPUS,
                eventDate,
                startTime,
                endTime,
                "EVT002"))
                .thenReturn(List.of(VenueAvailabilityDTO.builder()
                        .venueCode("SC001")
                        .campus(Campus.SOUTH_CAMPUS)
                        .venueName("Auditorium")
                        .venueType("Auditorium")
                        .capacity(850)
                        .build()));

        mockMvc.perform(get("/api/executive/venues/available")
                        .param("campus", "SOUTH_CAMPUS")
                        .param("eventDate", "2026-08-10")
                        .param("startTime", "10:00")
                        .param("endTime", "12:00")
                        .param("excludeEventID", "EVT002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].venueCode").value("SC001"));

        verify(venueService).getAvailableVenues(
                Campus.SOUTH_CAMPUS,
                eventDate,
                startTime,
                endTime,
                "EVT002");
    }

    @Test
    @WithMockUser(username = "executive@nmu.ac.za", roles = "STUDENT")
    void currentEditClientEventIdAliasStillExcludesItsReservation()
            throws Exception {
        LocalDate eventDate = LocalDate.of(2026, 8, 10);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(12, 0);
        when(venueService.getAvailableVenues(
                Campus.SOUTH_CAMPUS,
                eventDate,
                startTime,
                endTime,
                "EVT002"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/executive/venues/available")
                        .param("campus", "SOUTH_CAMPUS")
                        .param("eventDate", "2026-08-10")
                        .param("startTime", "10:00")
                        .param("endTime", "12:00")
                        .param("eventID", "EVT002"))
                .andExpect(status().isOk());

        verify(venueService).getAvailableVenues(
                Campus.SOUTH_CAMPUS,
                eventDate,
                startTime,
                endTime,
                "EVT002");
    }
}
