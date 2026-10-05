package com.societycentral.repository;

import com.societycentral.model.SocietyHighlight;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SocietyHighlightRepository
        extends JpaRepository<SocietyHighlight, String> {

    @EntityGraph(attributePaths = "coverMedia")
    List<SocietyHighlight>
    findBySocietyIDOrderBySortOrderAscCreatedAtAscHighlightIDAsc(
            String societyID);

    @EntityGraph(attributePaths = "coverMedia")
    List<SocietyHighlight>
    findBySocietyIDAndActiveStatusTrueOrderBySortOrderAscPublishedAtDescHighlightIDAsc(
            String societyID);

    @EntityGraph(attributePaths = "coverMedia")
    Optional<SocietyHighlight> findByHighlightIDAndSocietyID(
            String highlightID,
            String societyID);

    @EntityGraph(attributePaths = "coverMedia")
    Optional<SocietyHighlight>
    findByHighlightIDAndSocietyIDAndActiveStatusTrue(
            String highlightID,
            String societyID);

    @Query("""
            select coalesce(max(highlight.sortOrder), -1)
            from SocietyHighlight highlight
            where highlight.societyID = :societyID
            """)
    int findMaximumSortOrder(@Param("societyID") String societyID);
}
