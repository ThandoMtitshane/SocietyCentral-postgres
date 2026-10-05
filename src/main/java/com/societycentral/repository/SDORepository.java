package com.societycentral.repository;

import com.societycentral.model.SDO;
import com.societycentral.repository.projection.SdoDirectoryView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SDORepository extends JpaRepository<SDO, String> {

    // ID type = String (staffNumber)

    Optional<SDO> findByEmail(String email);

    /**
     * Directory search across SDOs by name, excluding the caller. A null
     * pattern returns every SDO. Name is sourced from the linked User.
     *
     * @param searchPattern lowercased {@code %term%} pattern, or null for all
     * @param excludeStaffNumber the caller's staff number (excluded)
     */
    @Query("""
            select sdo.staffNumber as staffNumber,
                   user.email as email,
                   user.firstName as firstName,
                   user.lastName as lastName
            from SDO sdo
            join User user on user.email = sdo.email
            where sdo.staffNumber <> :excludeStaffNumber
              and (:searchPattern is null
                   or lower(user.firstName) like :searchPattern
                   or lower(user.lastName) like :searchPattern
                   or lower(concat(user.firstName, ' ', user.lastName))
                        like :searchPattern)
            order by user.firstName asc, user.lastName asc
            """)
    List<SdoDirectoryView> searchDirectory(
            @Param("searchPattern") String searchPattern,
            @Param("excludeStaffNumber") String excludeStaffNumber);

    /** Loads an SDO by staff number with their linked User eagerly fetched. */
    @Query("""
            select sdo
            from SDO sdo
            join fetch sdo.user
            where sdo.staffNumber = :staffNumber
            """)
    Optional<SDO> findByStaffNumberWithUser(
            @Param("staffNumber") String staffNumber);

    /**
     * Checks whether a phone extension is already assigned to an SDO.
     */
    boolean existsByPhoneExtension(String phoneExtension);

    /**
     * Checks whether another SDO already uses the phone extension.
     *
     * The current SDO is excluded using the staff number.
     */
    boolean existsByPhoneExtensionAndStaffNumberNot(
            String phoneExtension,
            String staffNumber
    );

    Boolean existsByEmail(String email);
}