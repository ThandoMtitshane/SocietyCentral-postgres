package com.societycentral.repository;

import com.societycentral.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository responsible for performing database operations
 * on the AuditLog table.
 *
 * Spring Data JPA automatically provides implementations for:
 *
 * • save()
 * • findById()
 * • findAll()
 * • delete()
 * • existsById()
 *
 * Additional query methods can be added as the application grows.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {

}

