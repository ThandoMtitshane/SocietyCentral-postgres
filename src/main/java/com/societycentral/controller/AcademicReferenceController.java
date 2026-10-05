package com.societycentral.controller;
import com.societycentral.dto.response.*;
import com.societycentral.service.AcademicReferenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/reference")
public class AcademicReferenceController {
 private final AcademicReferenceService service; public AcademicReferenceController(AcademicReferenceService s){service=s;}
 @GetMapping("/faculties") public ResponseEntity<ApiResponse<List<AcademicReferenceResponses.Faculty>>> faculties(){return ResponseEntity.ok(ApiResponse.success("Faculties retrieved successfully.",service.faculties()));}
 @GetMapping("/campuses") public ResponseEntity<ApiResponse<List<AcademicReferenceResponses.Campus>>> campuses(){return ResponseEntity.ok(ApiResponse.success("Campuses retrieved successfully.",service.campuses()));}
 @GetMapping("/programmes") public ResponseEntity<ApiResponse<List<AcademicReferenceResponses.Programme>>> programmes(){return ResponseEntity.ok(ApiResponse.success("Programmes retrieved successfully.",service.programmes()));}
 @GetMapping("/programmes/{code}") public ResponseEntity<ApiResponse<AcademicReferenceResponses.Programme>> programme(@PathVariable String code){return ResponseEntity.ok(ApiResponse.success("Programme retrieved successfully.",service.programme(code)));}
 @GetMapping("/programmes/{code}/campuses") public ResponseEntity<ApiResponse<List<AcademicReferenceResponses.Campus>>> programmeCampuses(@PathVariable String code){return ResponseEntity.ok(ApiResponse.success("Programme campuses retrieved successfully.",service.programmeCampuses(code)));}
 @GetMapping("/faculties/{code}/schools") public ResponseEntity<ApiResponse<List<AcademicReferenceResponses.School>>> schools(@PathVariable String code){return ResponseEntity.ok(ApiResponse.success("Schools retrieved successfully.",service.schools(code)));}
 @GetMapping("/accommodation-types") public ResponseEntity<ApiResponse<List<AcademicReferenceResponses.AccommodationType>>> accommodationTypes(){return ResponseEntity.ok(ApiResponse.success("Accommodation types retrieved successfully.",service.accommodationTypes()));}
 @GetMapping("/campuses/{code}/residences") public ResponseEntity<ApiResponse<List<AcademicReferenceResponses.Residence>>> residences(@PathVariable String code){return ResponseEntity.ok(ApiResponse.success("Residences retrieved successfully.",service.residences(code)));}
 @GetMapping("/off-campus-properties/accredited") public ResponseEntity<ApiResponse<List<AcademicReferenceResponses.Property>>> accreditedProperties(){return ResponseEntity.ok(ApiResponse.success("Accredited properties retrieved successfully.",service.accreditedProperties()));}
}
