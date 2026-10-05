package com.societycentral.repository;
import com.societycentral.model.CampusReference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CampusReferenceRepository extends JpaRepository<CampusReference,String> { List<CampusReference> findByActiveTrueOrderByCampusNameAsc(); }
