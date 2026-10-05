package com.societycentral.repository;
import com.societycentral.model.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProgrammeCampusRepository extends JpaRepository<ProgrammeCampus,ProgrammeCampusId> {
    @EntityGraph(attributePaths = {"programme", "campus"})
    List<ProgrammeCampus> findByProgrammeProgrammeCode(String code);

    @EntityGraph(attributePaths = {"programme", "campus"})
    List<ProgrammeCampus> findByProgrammeProgrammeCodeIn(Collection<String> codes);
}
