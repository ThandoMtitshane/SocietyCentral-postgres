package com.societycentral.repository;
import com.societycentral.model.ProgrammeReference;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProgrammeReferenceRepository extends JpaRepository<ProgrammeReference,String> {
    @EntityGraph(attributePaths = {"faculty", "school"})
    List<ProgrammeReference> findByActiveTrueOrderByProgrammeNameAsc();

    @EntityGraph(attributePaths = {"faculty", "school"})
    Optional<ProgrammeReference> findByProgrammeCode(String programmeCode);
}
