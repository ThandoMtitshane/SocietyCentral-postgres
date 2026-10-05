package com.societycentral.repository;
import com.societycentral.model.OffCampusAccreditation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OffCampusAccreditationRepository extends JpaRepository<OffCampusAccreditation, com.societycentral.model.OffCampusAccreditationId> { List<OffCampusAccreditation> findByAcademicYearAndAccreditedTrue(Short academicYear); }
