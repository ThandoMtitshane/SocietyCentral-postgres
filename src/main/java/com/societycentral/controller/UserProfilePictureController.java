package com.societycentral.controller;

import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.model.UserProfilePicture;
import com.societycentral.repository.StudentRepository;
import com.societycentral.repository.UserProfilePictureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserProfilePictureController {
    private final StudentRepository students;
    private final UserProfilePictureRepository pictures;

    @GetMapping("/api/users/students/{studentNumber}/profile-picture")
    public ResponseEntity<byte[]> get(@PathVariable String studentNumber) {
        String email = students.findById(studentNumber).map(s -> s.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        UserProfilePicture picture = pictures.findById(email)
                .orElseThrow(() -> new ResourceNotFoundException("Profile picture not found."));
        return ResponseEntity.ok().header("Content-Type", picture.getContentType()).body(picture.getImageData());
    }
}
