package com.societycentral.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Persisted media belonging to a society profile gallery.
 *
 * <p>Logo and banner URLs remain on {@link Society}; this table stores the
 * repeatable gallery collection and its presentation metadata.</p>
 */
@Entity
@Table(name = "SocietyMedia")
@Getter
@Setter
@NoArgsConstructor
public class SocietyMedia {

    @Id
    @Column(name = "mediaID", length = 36)
    private String mediaID;

    @Column(name = "societyID", length = 20, nullable = false)
    private String societyID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "societyID",
            referencedColumnName = "societyID",
            insertable = false,
            updatable = false)
    private Society society;

    @Column(name = "mediaUrl", length = 500, nullable = false)
    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "mediaType", length = 30, nullable = false)
    private SocietyImageType mediaType;

    @Column(name = "caption", length = 200)
    private String caption;

    @Column(name = "sortOrder", nullable = false)
    private Integer sortOrder;

    @Column(name = "uploadedAt", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "uploadedBy", length = 100, nullable = false)
    private String uploadedBy;
}
