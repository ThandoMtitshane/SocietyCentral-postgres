package com.societycentral.repository;

import com.societycentral.model.ExecutiveEventReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExecutiveEventReportRepository extends JpaRepository<ExecutiveEventReport, String> {

    // Check if a report already exists for this event+society (enforces 1 per society per event)
    boolean existsByEventIDAndSocietyID(String eventID, String societyID);

    // Find existing report for viewing
    Optional<ExecutiveEventReport> findByEventIDAndSocietyID(String eventID, String societyID);
}
