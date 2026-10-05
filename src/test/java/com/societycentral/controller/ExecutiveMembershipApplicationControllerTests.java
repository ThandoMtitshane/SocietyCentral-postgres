package com.societycentral.controller;

import com.societycentral.dto.response.ExecutiveMembershipApplicationDetailsDTO;
import com.societycentral.dto.response.ExecutiveMembershipApplicationPageDTO;
import com.societycentral.exception.GlobalExceptionHandler;
import com.societycentral.model.MembershipApplicationStatus;
import com.societycentral.security.JwtUtil;
import com.societycentral.service.ExecutiveMembershipApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutiveMembershipApplicationController.class)
@Import({
        GlobalExceptionHandler.class,
        ExecutiveMembershipApplicationControllerTests.MethodSecurityConfig.class
})
class ExecutiveMembershipApplicationControllerTests {

    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    private static final String EXECUTIVE_EMAIL = "executive@nmu.ac.za";
    private static final String APPLICATION_ID =
            "c0f6b48a-e2ad-4b89-b9c6-7de7a2b22112";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExecutiveMembershipApplicationService applicationService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = EXECUTIVE_EMAIL, roles = "STUDENT")
    void listDefaultsToPendingAndAuthenticatedIdentity() throws Exception {
        ExecutiveMembershipApplicationPageDTO page =
                ExecutiveMembershipApplicationPageDTO.builder()
                        .content(List.of())
                        .page(0)
                        .size(20)
                        .totalElements(0)
                        .totalPages(0)
                        .first(true)
                        .last(true)
                        .empty(true)
                        .build();
        when(applicationService.getApplications(
                EXECUTIVE_EMAIL,
                MembershipApplicationStatus.PENDING,
                null,
                0,
                20)).thenReturn(page);

        mockMvc.perform(get("/api/executive/membership-applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value(
                        "Membership applications retrieved successfully."))
                .andExpect(jsonPath("$.data.empty").value(true));

        verify(applicationService).getApplications(
                EXECUTIVE_EMAIL,
                MembershipApplicationStatus.PENDING,
                null,
                0,
                20);
    }

    @Test
    @WithMockUser(username = EXECUTIVE_EMAIL, roles = "STUDENT")
    void detailsReturnApplicantContract() throws Exception {
        when(applicationService.getApplication(
                EXECUTIVE_EMAIL, APPLICATION_ID))
                .thenReturn(ExecutiveMembershipApplicationDetailsDTO.builder()
                        .applicationID(APPLICATION_ID)
                        .firstName("Akhona")
                        .lastName("Mbeki")
                        .fullName("Akhona Mbeki")
                        .email("applicant@nmu.ac.za")
                        .status(MembershipApplicationStatus.PENDING)
                        .build());

        mockMvc.perform(get(
                        "/api/executive/membership-applications/{id}",
                        APPLICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationID")
                        .value(APPLICATION_ID))
                .andExpect(jsonPath("$.data.firstName").value("Akhona"))
                .andExpect(jsonPath("$.data.fullName")
                        .value("Akhona Mbeki"))
                .andExpect(jsonPath("$.data.email")
                        .value("applicant@nmu.ac.za"));
    }

    @Test
    @WithMockUser(username = EXECUTIVE_EMAIL, roles = "STUDENT")
    void approveUsesOnlyPathAndAuthenticatedIdentity() throws Exception {
        when(applicationService.approveApplication(
                EXECUTIVE_EMAIL, APPLICATION_ID))
                .thenReturn(ExecutiveMembershipApplicationDetailsDTO.builder()
                        .applicationID(APPLICATION_ID)
                        .status(MembershipApplicationStatus.APPROVED)
                        .build());

        mockMvc.perform(post(
                        "/api/executive/membership-applications/{id}/approve",
                        APPLICATION_ID)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Membership application approved successfully."))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        verify(applicationService).approveApplication(
                EXECUTIVE_EMAIL, APPLICATION_ID);
    }

    @Test
    @WithMockUser(username = EXECUTIVE_EMAIL, roles = "STUDENT")
    void blankRejectionReasonReturnsBadRequestBeforeService() throws Exception {
        mockMvc.perform(post(
                        "/api/executive/membership-applications/{id}/reject",
                        APPLICATION_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectionReason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("Rejection reason is required."));

        verify(applicationService, never()).rejectApplication(
                any(String.class),
                any(String.class),
                any());
    }

    @Test
    @WithMockUser(username = "sdo@nmu.ac.za", roles = "SDO")
    void nonStudentAuthorityCannotUseExecutiveReviewEndpoints()
            throws Exception {
        mockMvc.perform(get("/api/executive/membership-applications"))
                .andExpect(status().isForbidden());

        verify(applicationService, never()).getApplications(
                any(String.class),
                any(),
                any(),
                eq(0),
                eq(20));
    }
}
