package com.societycentral.controller;

import com.societycentral.dto.response.PublicExecutiveProfileResponse;
import com.societycentral.service.PublicExecutiveProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class PublicExecutiveProfileController {
    private final PublicExecutiveProfileService profileService;

    @GetMapping("/api/users/{studentNumber}/profile")
    public PublicExecutiveProfileResponse get(@PathVariable String studentNumber) {
        return profileService.get(studentNumber);
    }
}
