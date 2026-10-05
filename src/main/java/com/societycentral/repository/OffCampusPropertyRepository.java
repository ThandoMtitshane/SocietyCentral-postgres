package com.societycentral.repository;
import com.societycentral.model.OffCampusProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OffCampusPropertyRepository extends JpaRepository<OffCampusProperty, Integer> { List<OffCampusProperty> findByActiveTrueOrderByPropertyNameAsc(); }
