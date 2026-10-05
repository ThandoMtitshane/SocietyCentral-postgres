package com.societycentral.controller;

import com.societycentral.dto.response.SocietyResponseDTO;
import com.societycentral.exception.GlobalExceptionHandler;
import com.societycentral.security.JwtUtil;
import com.societycentral.service.SocietyManagementService;
import com.societycentral.service.SocietyMediaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that internal SDO society responses are never available to students.
 */
@WebMvcTest(SocietyManagementController.class)
@Import({
        GlobalExceptionHandler.class,
        SocietyManagementControllerSecurityTests.MethodSecurityConfiguration.class
})
class SocietyManagementControllerSecurityTests {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SocietyManagementService societyManagementService;

    @MockitoBean
    private SocietyMediaService societyMediaService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = "student@nmu.ac.za", roles = "STUDENT")
    void studentCannotReadInternalSdoSocietyResponses() throws Exception {
        mockMvc.perform(get("/api/sdo/societies"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(societyManagementService);
    }

    @Test
    @WithMockUser(username = "sdo@nmu.ac.za", roles = "SDO")
    void sdoCanUseExistingSocietyManagementRoute() throws Exception {
        when(societyManagementService.getAllSocieties())
                .thenReturn(List.of(
                        SocietyResponseDTO.builder()
                                .societyID("SOC001")
                                .numberOfMembers(42)
                                .build()));

        mockMvc.perform(get("/api/sdo/societies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].societyID")
                        .value("SOC001"))
                .andExpect(jsonPath("$.data[0].numberOfMembers")
                        .value(42));
    }
}
