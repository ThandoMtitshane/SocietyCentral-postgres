package com.societycentral.repository;

import com.societycentral.model.Executive;
import com.societycentral.model.ExecutiveId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Persistence queries for current and historical society executive terms.
 */
@Repository
public interface ExecutiveRepository extends JpaRepository<Executive, ExecutiveId> {
    // ID type = ExecutiveId (studentNumber, societyID, termStartDate)

    // All executive terms (past and present) for a given student
    List<Executive> findByIdStudentNumber(String studentNumber);

    // All executives (across all terms) for a given society
    List<Executive> findByIdSocietyID(String societyID);

    @Query("SELECT e FROM Executive e " +
            "LEFT JOIN FETCH e.student s " +
            "LEFT JOIN FETCH s.user u " +
            "WHERE e.id.societyID = :societyID")
    List<Executive> findByIdSocietyIDWithUser(@Param("societyID") String societyID);

    List<Executive> findBySocietySocietyIDAndTermEndDateGreaterThanEqual(String societyID, LocalDate today);

    List<Executive> findBySocietySocietyIDAndTermEndDateIsNull(String societyID);

    /**
     * Returns public executive rows whose term has not ended.
     */
    @Query("""
            select distinct executive
            from Executive executive
            join fetch executive.student student
            left join fetch student.user
            where executive.id.societyID = :societyID
              and executive.id.termStartDate <= :currentDate
              and (executive.termEndDate is null
                   or executive.termEndDate >= :currentDate)
            order by executive.position asc,
                     executive.id.studentNumber asc
            """)
    List<Executive> findActiveExecutivesForSocietyProfile(
            @Param("societyID") String societyID,
            @Param("currentDate") LocalDate currentDate);

    /**
     * Checks active ownership of one requested society.
     */
    @Query("""
            select count(executive) > 0
            from Executive executive
            where executive.id.studentNumber = :studentNumber
              and executive.id.societyID = :societyID
              and executive.id.termStartDate <= :currentDate
              and (executive.termEndDate is null
                   or executive.termEndDate >= :currentDate)
            """)
    boolean existsActiveExecutiveRole(
            @Param("studentNumber") String studentNumber,
            @Param("societyID") String societyID,
            @Param("currentDate") LocalDate currentDate);

    @Query("SELECT e FROM Executive e WHERE e.society.societyID = :societyID " +
            "AND e.id.termStartDate <= :today " +
            "AND (e.termEndDate IS NULL OR e.termEndDate >= :today)")
    List<Executive> findCurrentExecutivesBySociety(@Param("societyID") String societyID,
                                                   @Param("today") LocalDate today);
    /**
     * Returns the email addresses of executives serving the society today.
     *
     * @param societyID selected society identifier
     * @param currentDate server-local current date
     * @return distinct current executive email addresses
     */
    @Query("""
            select distinct student.email
            from Executive executive
            join executive.student student
            where executive.id.societyID = :societyID
              and executive.id.termStartDate <= :currentDate
              and (executive.termEndDate is null
                   or executive.termEndDate >= :currentDate)
            """)
    List<String> findCurrentExecutiveEmailsBySocietyID(
            @Param("societyID") String societyID,
            @Param("currentDate") LocalDate currentDate);

    /**
     * Returns active executive roles for a student, newest term first.
     *
     * @param studentNumber authenticated student's number
     * @param currentDate server-local current date
     * @return roles whose start and end dates include the current date
     */
    @Query("""
            select executive
            from Executive executive
            where executive.id.studentNumber = :studentNumber
              and executive.id.termStartDate <= :currentDate
              and (executive.termEndDate is null
                   or executive.termEndDate >= :currentDate)
            order by executive.id.termStartDate desc
            """)
    List<Executive> findActiveExecutiveRoles(
            @Param("studentNumber") String studentNumber,
            @Param("currentDate") LocalDate currentDate);

    /**
     * Directory search across current executives by name, portfolio, or
     * society. Excludes the caller. A null pattern returns every current
     * executive.
     *
     * @param searchPattern lowercased {@code %term%} pattern, or null for all
     * @param excludeStudentNumber the caller's student number (excluded)
     * @param currentDate server-local current date bounding active terms
     */
    @Query("""
            select student.studentNumber as studentNumber,
                   student.email as email,
                   user.firstName as firstName,
                   user.lastName as lastName,
                   executive.position as position,
                   society.societyID as societyID,
                   society.societyName as societyName
            from Executive executive
            join executive.student student
            join student.user user
            join executive.society society
            where executive.id.termStartDate <= :currentDate
              and (executive.termEndDate is null
                   or executive.termEndDate >= :currentDate)
              and society.activeStatus = true
              and student.studentNumber <> :excludeStudentNumber
              and (:searchPattern is null
                   or lower(user.firstName) like :searchPattern
                   or lower(user.lastName) like :searchPattern
                   or lower(concat(user.firstName, ' ', user.lastName))
                        like :searchPattern
                   or lower(executive.position) like :searchPattern
                   or lower(society.societyName) like :searchPattern
                   or lower(society.societyID) like :searchPattern)
            order by user.firstName asc, user.lastName asc
            """)
    List<com.societycentral.repository.projection.ExecutiveDirectoryView>
    searchDirectory(
            @Param("searchPattern") String searchPattern,
            @Param("excludeStudentNumber") String excludeStudentNumber,
            @Param("currentDate") LocalDate currentDate);

    /**
     * Directory search restricted to a set of societies (the caller's own
     * society for executives, or an SDO's supervised societies). Excludes the
     * caller. A null pattern returns every current executive in those
     * societies.
     *
     * @param societyIDs societies whose current executives are searchable
     * @param searchPattern lowercased {@code %term%} pattern, or null for all
     * @param excludeStudentNumber the caller's student number (excluded)
     * @param currentDate server-local current date bounding active terms
     */
    @Query("""
            select student.studentNumber as studentNumber,
                   student.email as email,
                   user.firstName as firstName,
                   user.lastName as lastName,
                   executive.position as position,
                   society.societyID as societyID,
                   society.societyName as societyName
            from Executive executive
            join executive.student student
            join student.user user
            join executive.society society
            where executive.id.societyID in :societyIDs
              and executive.id.termStartDate <= :currentDate
              and (executive.termEndDate is null
                   or executive.termEndDate >= :currentDate)
              and society.activeStatus = true
              and student.studentNumber <> :excludeStudentNumber
              and (:searchPattern is null
                   or lower(user.firstName) like :searchPattern
                   or lower(user.lastName) like :searchPattern
                   or lower(concat(user.firstName, ' ', user.lastName))
                        like :searchPattern
                   or lower(executive.position) like :searchPattern
                   or lower(society.societyName) like :searchPattern
                   or lower(society.societyID) like :searchPattern)
            order by society.societyName asc, user.firstName asc, user.lastName asc
            """)
    List<com.societycentral.repository.projection.ExecutiveDirectoryView>
    searchDirectoryInSocieties(
            @Param("societyIDs") java.util.Collection<String> societyIDs,
            @Param("searchPattern") String searchPattern,
            @Param("excludeStudentNumber") String excludeStudentNumber,
            @Param("currentDate") LocalDate currentDate);
}
