package com.societycentral.dto.response;

import com.societycentral.model.MentionType;
import lombok.Builder;
import lombok.Getter;

/**
 * A resolved mention for rendering: the type plus a display label and the
 * target id the frontend links to (a user profile or an event page).
 */
@Getter
@Builder
public class MentionView {
    private MentionType mentionType;
    private String targetStudentNumber;
    private String targetEventID;
    /** Human-readable label, e.g. the person's name or the event name. */
    private String label;
}
