// com.societycentral.controller.StudentController.java
package com.societycentral.controller;

import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.StudentDTO;
import com.societycentral.model.Student;
import com.societycentral.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentRepository studentRepository;

    /**
     * Get student details by student number
     * GET /api/students/{studentNumber}
     */
    @GetMapping("/{studentNumber}")
    public ResponseEntity<ApiResponse<StudentDTO>> getStudent(@PathVariable String studentNumber) {
        Student student = studentRepository.findById(studentNumber)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentNumber));

        StudentDTO dto = new StudentDTO();
        dto.setStudentNumber(student.getStudentNumber());
        dto.setEmail(student.getEmail());

        // Get name from User relationship
        if (student.getUser() != null) {
            dto.setFirstName(student.getUser().getFirstName());
            dto.setLastName(student.getUser().getLastName());
        } else {
            dto.setFirstName("Unknown");
            dto.setLastName("");
        }

        return ResponseEntity.ok(ApiResponse.success("Student found", dto));
    }
}
