package com.societycentral.repository;

import com.societycentral.model.MembershipApplication;
import com.societycentral.model.MembershipApplicationStatus;
import com.societycentral.repository.projection.ExecutiveMembershipApplicationListProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence queries for student membership applications.
 */
@Repository
public interface MembershipApplicationRepository
        extends JpaRepository<MembershipApplication, String> {

    /**
     * Finds one application for a student, society and workflow status.
     *
     * @param studentNumber authenticated student's number
     * @param societyID selected society identifier
     * @param status required application status
     * @return a matching application when present
     */
    Optional<MembershipApplication>
    findFirstByStudentNumberAndSocietyIDAndStatus(
            String studentNumber,
            String societyID,
            MembershipApplicationStatus status);

    /**
     * Checks whether an application exists for a student, society and status.
     *
     * @param studentNumber authenticated student's number
     * @param societyID selected society identifier
     * @param status required application status
     * @return true when a matching application exists
     */
    boolean existsByStudentNumberAndSocietyIDAndStatus(
            String studentNumber,
            String societyID,
            MembershipApplicationStatus status);

    /**
     * Returns a student's complete application history newest first.
     *
     * @param studentNumber authenticated student's number
     * @return application history
     */
    List<MembershipApplication>
    findByStudentNumberOrderByApplicationDateDesc(String studentNumber);

    /**
     * Resolves an application by its public tracking reference.
     *
     * @param trackingReference public tracking reference
     * @return the matching application when present
     */
    Optional<MembershipApplication>
    findByTrackingReference(String trackingReference);

    /**
     * Returns society applications in review order for the later approval flow.
     *
     * @param societyID society identifier
     * @param status required workflow status
     * @return applications ordered oldest first
     */
    List<MembershipApplication>
    findBySocietyIDAndStatusOrderByApplicationDateAsc(
            String societyID,
            MembershipApplicationStatus status);

    /**
     * Returns the latest application for one student and society.
     *
     * @param studentNumber authenticated student's number
     * @param societyID society identifier
     * @return the latest application when one exists
     */
    Optional<MembershipApplication>
    findFirstByStudentNumberAndSocietyIDOrderByApplicationDateDesc(
            String studentNumber,
            String societyID);

    /**
     * Returns one searched application page for an executive's society.
     *
     * <p>Application, Student, User and Society are joined in one query to
     * avoid per-row applicant lookups.</p>
     *
     * @param societyID authenticated executive's society identifier
     * @param status required workflow status
     * @param searchPattern optional lower-case LIKE pattern
     * @param pageable requested page and workflow ordering
     * @return matching application summaries
     */
    @Query(
            value = """
                    select
                        a.applicationID as applicationID,
                        a.trackingReference as trackingReference,
                        a.status as status,
                        a.applicationDate as applicationDate,
                        a.lastUpdatedAt as lastUpdatedAt,
                        student.studentNumber as studentNumber,
                        applicantUser.firstName as studentFirstName,
                        applicantUser.lastName as studentLastName,
                        student.course as course,
                        student.level as level,
                        applicantUser.campus as campus,
                        a.motivation as motivation,
                        society.societyID as societyID,
                        society.societyName as societyName
                    from MembershipApplication a
                    join Student student
                        on student.studentNumber = a.studentNumber
                    join student.user applicantUser
                    join Society society
                        on society.societyID = a.societyID
                    where a.societyID = :societyID
                      and a.status = :status
                      and (
                            :searchPattern is null
                            or lower(applicantUser.firstName)
                               like :searchPattern
                            or lower(applicantUser.lastName)
                               like :searchPattern
                            or lower(concat(
                                concat(applicantUser.firstName, ' '),
                                applicantUser.lastName
                            )) like :searchPattern
                            or lower(student.studentNumber)
                               like :searchPattern
                            or lower(a.trackingReference)
                               like :searchPattern
                      )
                    """,
            countQuery = """
                    select count(a)
                    from MembershipApplication a
                    join Student student
                        on student.studentNumber = a.studentNumber
                    join student.user applicantUser
                    where a.societyID = :societyID
                      and a.status = :status
                      and (
                            :searchPattern is null
                            or lower(applicantUser.firstName)
                               like :searchPattern
                            or lower(applicantUser.lastName)
                               like :searchPattern
                            or lower(concat(
                                concat(applicantUser.firstName, ' '),
                                applicantUser.lastName
                            )) like :searchPattern
                            or lower(student.studentNumber)
                               like :searchPattern
                            or lower(a.trackingReference)
                               like :searchPattern
                      )
                    """
    )
    Page<ExecutiveMembershipApplicationListProjection>
    findExecutiveApplications(
            @Param("societyID") String societyID,
            @Param("status") MembershipApplicationStatus status,
            @Param("searchPattern") String searchPattern,
            Pageable pageable);

    /**
     * Retrieves one application only when it belongs to the given society.
     *
     * @param applicationID application identifier
     * @param societyID authenticated executive's society identifier
     * @return society-owned application when present
     */
    Optional<MembershipApplication> findByApplicationIDAndSocietyID(
            String applicationID,
            String societyID);

    Optional<MembershipApplication> findByApplicationIDAndStudentNumber(
            String applicationID,
            String studentNumber);

    /**
     * Locks one application before an approval or rejection transition.
     *
     * @param applicationID application identifier
     * @return locked application when present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select application
            from MembershipApplication application
            where application.applicationID = :applicationID
            """)
    Optional<MembershipApplication> findByApplicationIDForUpdate(
            @Param("applicationID") String applicationID);

    /**
     * Counts applications for one society and workflow status.
     *
     * @param societyID society identifier
     * @param status workflow status
     * @return matching application count
     */
    long countBySocietyIDAndStatus(
            String societyID,
            MembershipApplicationStatus status);
}
