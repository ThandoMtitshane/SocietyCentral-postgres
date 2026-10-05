package com.societycentral.repository;
import com.societycentral.model.AccommodationType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AccommodationTypeRepository extends JpaRepository<AccommodationType, Integer> { List<AccommodationType> findByActiveTrueOrderByAccommodationTypeIDAsc(); }
