package com.societycentral.controller;

import com.societycentral.dto.request.UpdateSDORequestDTO;
import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.GetSDOResponse;
import com.societycentral.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.societycentral.dto.request.RegisterSDORequest;
import com.societycentral.dto.response.RegisterSDOResponse;
import org.springframework.web.bind.annotation.PostMapping;
import com.societycentral.dto.response.SDOListResponse;
import java.util.List;
/**
 * Controller responsible for handling administrator operations.
 * Provides endpoints that allow administrators to manage
 * Student Development Officers and perform administrative tasks.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class  AdminController {

    private final AdminService adminService;

    /**
     * Verifies that the authenticated user has administrator privileges.
     *
     * @return a success response containing "pong"
     */
    @GetMapping("/ping")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> ping() {
        return ResponseEntity.ok(ApiResponse.success("pong"));
    }

    /**
     * Retrieves the details of an existing Student Development Officer.
     *
     * @param staffNumber the unique staff number of the Student Development Officer
     * @return the Student Development Officer details
     */
    @GetMapping("/sdo/{staffNumber}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<GetSDOResponse>> getSDOByStaffNumber(
            @PathVariable String staffNumber) {

        GetSDOResponse response = adminService.getSDOByStaffNumber(staffNumber);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Registers a new Student Development Officer.
     *
     * @param request registration details
     * @return registration confirmation
     */
    @PostMapping("/sdo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RegisterSDOResponse>> registerSDO(
            @Valid @RequestBody RegisterSDORequest request) {

        RegisterSDOResponse response = adminService.registerSDO(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
    /**
     * Updates the details of an existing Student Development Officer.
     *
     * @param staffNumber the unique staff number of the Student Development Officer
     * @param request the updated Student Development Officer details
     * @return a success response if the update is completed successfully
     */
    @PutMapping("/sdo/{staffNumber}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateSDO(
            @PathVariable String staffNumber,
            @Valid @RequestBody UpdateSDORequestDTO request) {

        adminService.updateSDO(staffNumber, request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Student Development Officer updated successfully.",
                        null
                )
        );
    }


    /**
     * Retrieves all registered Student Development Officers.
     *
     * @return a list of Student Development Officers
     */
    @GetMapping("/sdo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SDOListResponse>>> getAllSDOs() {

        return ResponseEntity.ok(
                ApiResponse.success(adminService.getAllSDOs())
        );
    }

    /**
     * Deletes an existing Student Development Officer.
     *
     * @param staffNumber the unique staff number of the Student Development Officer
     * @return success response
     */
    @DeleteMapping("/sdo/{staffNumber}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteSDO(
            @PathVariable String staffNumber) {

        adminService.deleteSDO(staffNumber);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Student Development Officer deleted successfully."
                )
        );
    }

}

