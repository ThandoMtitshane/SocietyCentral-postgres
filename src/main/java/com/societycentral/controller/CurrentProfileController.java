package com.societycentral.controller;
import com.societycentral.dto.request.UpdateCurrentProfileRequest;
import com.societycentral.dto.request.ChangePasswordRequest;
import com.societycentral.dto.response.*;
import com.societycentral.service.CurrentProfileService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;

@RestController @RequestMapping("/api/profile")
public class CurrentProfileController {
    private final CurrentProfileService service;
    public CurrentProfileController(CurrentProfileService service) { this.service=service; }
    @GetMapping public ResponseEntity<ApiResponse<CurrentProfileResponse>> get(@AuthenticationPrincipal UserDetails u) { return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully.", service.get(u.getUsername()))); }
    @PutMapping public ResponseEntity<ApiResponse<CurrentProfileResponse>> update(@AuthenticationPrincipal UserDetails u, @Valid @RequestBody UpdateCurrentProfileRequest r) { return ResponseEntity.ok(ApiResponse.success("Profile updated successfully.", service.update(u.getUsername(), r))); }
    @PutMapping("/password") public ResponseEntity<ApiResponse<Void>> password(@AuthenticationPrincipal UserDetails u, @Valid @RequestBody ChangePasswordRequest r) { service.changePassword(u.getUsername(), r); return ResponseEntity.ok(ApiResponse.success("Password changed successfully.", null)); }
    @PostMapping(value="/picture", consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<ApiResponse<CurrentProfileResponse>> picture(@AuthenticationPrincipal UserDetails u, @RequestPart("file") MultipartFile f) { return ResponseEntity.ok(ApiResponse.success("Profile picture updated successfully.", service.upload(u.getUsername(), f))); }
    @GetMapping("/picture") public ResponseEntity<byte[]> picture(@AuthenticationPrincipal UserDetails u) { var picture=service.picture(u.getUsername()); return ResponseEntity.ok().contentType(MediaType.parseMediaType(picture.contentType())).contentLength(picture.data().length).header(HttpHeaders.CONTENT_DISPOSITION, "inline").body(picture.data()); }
    @DeleteMapping("/picture") public ResponseEntity<ApiResponse<CurrentProfileResponse>> remove(@AuthenticationPrincipal UserDetails u) { return ResponseEntity.ok(ApiResponse.success("Profile picture removed successfully.", service.removePicture(u.getUsername()))); }
}
