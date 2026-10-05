package com.societycentral.repository;
import com.societycentral.model.ResidenceReference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ResidenceReferenceRepository extends JpaRepository<ResidenceReference, Integer> { List<ResidenceReference> findByCampusCodeAndActiveTrueOrderByResidenceNameAsc(String campusCode); }
