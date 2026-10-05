package com.societycentral.repository;

import com.societycentral.model.SocietyMember;
import com.societycentral.model.SocietyMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Persistence queries for approved society memberships.
 */
@Repository
public interface SocietyMemberRepository extends JpaRepository<SocietyMember, SocietyMemberId> {
    // ID type = SocietyMemberId (studentNumber, societyID)

    // All societies a given student belongs to
    List<SocietyMember> findByIdStudentNumber(String studentNumber);

    // All members of a given society
    List<SocietyMember> findByIdSocietyID(String societyID);

    @Query("""
            select m from SocietyMember m join fetch m.student s join fetch s.user u
            where m.id.societyID = :societyID
              and (m.joinDate is null or m.joinDate <= :currentDate)
              and (m.expireDate is null or m.expireDate >= :currentDate)
            order by u.lastName, u.firstName
            """)
    List<SocietyMember> findCurrentMembersBySocietyID(@Param("societyID") String societyID, @Param("currentDate") LocalDate currentDate);
    Integer countAllByExpireDateAfterAndIdSocietyID(LocalDate now,String SocietyId);

    /**
     * Counts current approved membership rows for one society.
     */
    @Query("""
            select count(membership)
            from SocietyMember membership
            where membership.id.societyID = :societyID
              and (membership.joinDate is null
                   or membership.joinDate <= :currentDate)
              and (membership.expireDate is null
                   or membership.expireDate >= :currentDate)
            """)
    long countCurrentMembersBySocietyID(
            @Param("societyID") String societyID,
            @Param("currentDate") LocalDate currentDate);

    /**
     * Returns the society identifiers for memberships that are active today.
     *
     * @param studentNumber authenticated student's number
     * @param currentDate server-local current date
     * @return distinct society identifiers for current memberships
     */
    @Query("""
            select distinct membership.id.societyID
            from SocietyMember membership
            where membership.id.studentNumber = :studentNumber
              and (membership.joinDate is null
                   or membership.joinDate <= :currentDate)
              and (membership.expireDate is null
                   or membership.expireDate >= :currentDate)
            """)
    List<String> findActiveSocietyIDsForStudent(
            @Param("studentNumber") String studentNumber,
            @Param("currentDate") LocalDate currentDate);

    /**
     * Checks for an approved membership that has not expired.
     *
     * @param studentNumber authenticated student's number
     * @param societyID selected society identifier
     * @param currentDate server-local current date
     * @return true when a current membership exists
     */
    @Query("""
            select count(m) > 0
            from SocietyMember m
            where m.id.studentNumber = :studentNumber
              and m.id.societyID = :societyID
              and (m.joinDate is null or m.joinDate <= :currentDate)
              and (m.expireDate is null or m.expireDate >= :currentDate)
            """)
    boolean existsActiveMembership(
            @Param("studentNumber") String studentNumber,
            @Param("societyID") String societyID,
            @Param("currentDate") LocalDate currentDate);

    /** Returns current members from a bounded set of student numbers. */
    @Query("""
            select m.id.studentNumber
            from SocietyMember m
            where m.id.societyID = :societyID
              and m.id.studentNumber in :studentNumbers
              and (m.joinDate is null or m.joinDate <= :currentDate)
              and (m.expireDate is null or m.expireDate >= :currentDate)
            """)
    Set<String> findActiveStudentNumbersBySocietyIDAndStudentNumbers(
            @Param("societyID") String societyID,
            @Param("studentNumbers") Collection<String> studentNumbers,
            @Param("currentDate") LocalDate currentDate);

    /**
     * Checks for any approved membership row, including historical rows.
     *
     * @param studentNumber applicant student number
     * @param societyID society identifier
     * @return true when the composite membership key already exists
     */
    boolean existsByIdStudentNumberAndIdSocietyID(
            String studentNumber,
            String societyID);
}
