package com.societycentral.repository;

import com.societycentral.model.Society;
import org.apache.commons.csv.CSVParser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface SocietyRepository extends JpaRepository<Society, String> {
    // ID type = String (societyID)

    List<Society> findByActiveStatusTrue();

    /** Distinct staff numbers of the SDOs supervising the given societies. */
    @Query("""
            select distinct society.sdoStaffNumber
            from Society society
            where society.societyID in :societyIDs
              and society.sdoStaffNumber is not null
            """)
    List<String> findSupervisingSdoStaffNumbers(
            @Param("societyIDs") Collection<String> societyIDs);

    List<Society> findByFaculty(String faculty);

    List<Society> findBySchool(String school);

    List<Society> findByCampus(String campus);
    // All societies supervised by a given SDO
    List<Society> findBySdoStaffNumber(String sdoStaffNumber);

    List<Society> findAllBySdo_Email(String email);

    List<Society> findAllByIsFlaggedTrueAndFlaggedDateBefore(LocalDateTime flaggedDate);
}
