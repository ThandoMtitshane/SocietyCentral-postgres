package com.societycentral.repository;

import com.societycentral.model.Announcement;
import com.societycentral.model.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, String> {
    // ID type = String (announcementID)

    @Query("select a from Announcement a where a.targetType = :targetType and a.removed = false")
    List<Announcement> findByTargetType(@Param("targetType") TargetType targetType);

    // Active (not yet expired) announcements
    @Query("select a from Announcement a where a.removed = false and (a.expireDate is null or a.expireDate >= :now)")
    List<Announcement> findByExpireDateGreaterThanEqual(@Param("now") LocalDateTime now);

    @Query("""
            select a from Announcement a
            where a.removed = false
              and (a.publishAt is null or a.publishAt <= :now)
              and (a.expireDate is null or a.expireDate > :now)
            """)
    List<Announcement> findVisible(@Param("now") LocalDateTime now);

    @Query("select a from Announcement a where a.removed = false and (a.expireDate is null or a.expireDate >= :now)")
    List<Announcement> findActiveAnnouncements(@Param("now") LocalDateTime now);

    List<Announcement> findBySentBy(String sentByEmail);

    @Query("select a from Announcement a where a.removed = false and a.publishAt <= :now and a.expireDate > :now")
    List<Announcement> findDueForNotification(@Param("now") LocalDateTime now);

    /**
     * Returns active student announcements for a society profile.
     */
    @Query("""
            select announcement
            from Announcement announcement
            where announcement.targetType in :allowedTargets
              and (announcement.publishAt is null
                   or announcement.publishAt <= :currentDateTime)
              and (announcement.expireDate is null
                   or announcement.expireDate > :currentDateTime)
              and announcement.removed = false
              and announcement.society.societyID = :societyID
            order by announcement.datePosted desc,
                     announcement.announcementID desc
            """)
    List<Announcement> findProfileAnnouncements(
            @Param("societyID") String societyID,

            @Param("allowedTargets") java.util.Collection<TargetType> allowedTargets,
            @Param("currentDateTime") LocalDateTime currentDateTime,
            Pageable pageable);

    /** Backwards-compatible overload for existing callers/tests. */
    default List<Announcement> findProfileAnnouncements(
            String societyID, String sdoStaffNumber, TargetType targetType,
            LocalDateTime currentDateTime, Pageable pageable) {
        return findProfileAnnouncements(societyID, java.util.List.of(targetType),
                currentDateTime, pageable);
    }

    @Modifying(flushAutomatically = true)
    @Query("update Announcement a set a.sentBy = :newEmail where a.sentBy = :oldEmail")
    int reassignSenderEmail(@Param("oldEmail") String oldEmail,
                            @Param("newEmail") String newEmail);
}
