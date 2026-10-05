package com.societycentral.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Article-backed story displayed by the A500 Society Highlights carousel.
 * The cover remains a managed SocietyMedia asset; article copy is stored in
 * this record rather than overloading a media caption.
 */
@Entity
@Table(name = "SocietyHighlight")
@Getter
@Setter
@NoArgsConstructor
public class SocietyHighlight {

    @Id
    @Column(name = "highlightID", length = 36)
    private String highlightID;

    @Column(name = "societyID", length = 20, nullable = false)
    private String societyID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "societyID",
            referencedColumnName = "societyID",
            insertable = false,
            updatable = false)
    private Society society;

    /** Sanitised emphasis-only HTML; the 150 limit applies to visible text. */
    @Column(name = "headline", nullable = false, columnDefinition = "TEXT")
    private String headline;

    /** Sanitised emphasis-only HTML; the 300 limit applies to visible text. */
    @Column(name = "caption", nullable = false, columnDefinition = "TEXT")
    private String caption;

    /** Sanitised article HTML using the Society Highlight allow-list. */
    @Column(name = "article", nullable = false, columnDefinition = "TEXT")
    private String article;

    @Column(name = "coverMediaID", length = 36, nullable = false)
    private String coverMediaID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "coverMediaID",
            referencedColumnName = "mediaID",
            insertable = false,
            updatable = false)
    private SocietyMedia coverMedia;

    @Column(name = "category", length = 80)
    private String category;

    @Column(name = "sortOrder", nullable = false)
    private int sortOrder;

    @Column(name = "publishedAt")
    private LocalDateTime publishedAt;

    @Column(name = "activeStatus", nullable = false)
    private Boolean activeStatus = true;

    @Column(name = "createdBy", length = 100, nullable = false)
    private String createdBy;

    @Column(name = "createdAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updatedAt", nullable = false)
    private LocalDateTime updatedAt;
}
