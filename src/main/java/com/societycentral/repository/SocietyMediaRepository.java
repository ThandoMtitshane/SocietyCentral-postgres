package com.societycentral.repository;

import com.societycentral.model.SocietyImageType;
import com.societycentral.model.SocietyMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence access for ordered society gallery media.
 */
@Repository
public interface SocietyMediaRepository
        extends JpaRepository<SocietyMedia, String> {

    List<SocietyMedia>
    findBySocietyIDAndMediaTypeOrderBySortOrderAscUploadedAtAscMediaIDAsc(
            String societyID,
            SocietyImageType mediaType);

    Optional<SocietyMedia> findByMediaIDAndSocietyIDAndMediaType(
            String mediaID,
            String societyID,
            SocietyImageType mediaType);

    @Query("""
            select coalesce(max(media.sortOrder), -1)
            from SocietyMedia media
            where media.societyID = :societyID
              and media.mediaType = :mediaType
            """)
    int findMaximumSortOrder(
            @Param("societyID") String societyID,
            @Param("mediaType") SocietyImageType mediaType);
}
