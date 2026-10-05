package com.societycentral.repository;
import com.societycentral.model.SchoolReference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SchoolReferenceRepository extends JpaRepository<SchoolReference,String> { List<SchoolReference> findByFacultyFacultyCodeAndActiveTrueOrderBySchoolNameAsc(String code); }
