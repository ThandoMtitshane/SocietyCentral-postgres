package com.societycentral.repository;

import com.societycentral.model.POA;
import com.societycentral.model.POAStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface POARepository extends JpaRepository<POA, String> {

    // Enforces 1 POA per society per year
    Optional<POA> findBySocietyIDAndYear(String societyID, Integer year);

    // All POAs for a society (history)
    List<POA> findBySocietyIDOrderByYearDesc(String societyID);

    // SDO views POAs for all their assigned societies
    List<POA> findBySocietyIDIn(List<String> societyIDs);

    // Count submitted POAs for SDO dashboard
    long countBySocietyIDInAndStatus(List<String> societyIDs, POAStatus status);
}