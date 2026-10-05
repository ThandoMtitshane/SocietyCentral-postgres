package com.societycentral.repository;
import com.societycentral.model.FacultyReference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface FacultyReferenceRepository extends JpaRepository<FacultyReference,String> { List<FacultyReference> findByActiveTrueOrderByFacultyNameAsc(); }
