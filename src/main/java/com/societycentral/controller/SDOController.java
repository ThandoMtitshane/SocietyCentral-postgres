package com.societycentral.controller;

import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.SocietySummaryDTO;
import com.societycentral.service.SDOService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sdo")
@RequiredArgsConstructor
public class SDOController {

    private final SDOService sdoService;

    /**
     * Returns the societies supervised by the authenticated SDO.
     * Used to populate the society-selection dropdown when sending
     * a targeted announcement.
     */
    @GetMapping("/supervised-societies")
    public ResponseEntity<ApiResponse<List<SocietySummaryDTO>>> getSupervisedSocieties(
            Authentication authentication) {

        List<SocietySummaryDTO> societies =
                sdoService.getSupervisedSocieties(authentication.getName());

        return ResponseEntity.ok(
                ApiResponse.success("Supervised societies retrieved successfully", societies));
    }
}