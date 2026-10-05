package com.societycentral.controller;

import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.ExecutiveMemberListItemDTO;
import com.societycentral.service.SocietyMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/executive/members")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class ExecutiveMemberController {
    private final SocietyMemberService memberService;

    @GetMapping
    public ApiResponse<List<ExecutiveMemberListItemDTO>> getMembers(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success("Current society members retrieved successfully.",
                memberService.findCurrentMembersForExecutive(userDetails.getUsername()));
    }

    @PostMapping("/{studentNumber}/remove")
    public ApiResponse<Void> removeMember(
            @PathVariable String studentNumber,
            @AuthenticationPrincipal UserDetails userDetails) {
        memberService.removeMemberForExecutive(userDetails.getUsername(), studentNumber);
        return ApiResponse.success("Society membership ended successfully.", null);
    }
}
