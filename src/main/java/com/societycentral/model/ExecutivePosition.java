package com.societycentral.model;

import java.util.Locale;

/**
 * Canonical executive positions used for access checks and profile ordering.
 *
 * <p>The database continues to store {@link Executive#position} as text.
 * Parsing is deliberately tolerant of legacy title case, spaces, hyphens,
 * and underscores; no stored value is renamed by this enum.</p>
 */
public enum ExecutivePosition {
    PRESIDENT(0, true),
    VICE_PRESIDENT(1, false),
    DEPUTY_PRESIDENT(1, false),
    SECRETARY(2, true),
    DEPUTY_SECRETARY(3, false),
    TREASURER(4, false),
    PRO(5, false),
    UNKNOWN(100, false);

    private final int profileOrder;
    private final boolean profileEditor;

    ExecutivePosition(int profileOrder, boolean profileEditor) {
        this.profileOrder = profileOrder;
        this.profileEditor = profileEditor;
    }

    /**
     * Resolves a persisted free-text position without changing its value.
     */
    public static ExecutivePosition fromStoredValue(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        String compact = value.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");

        return switch (compact) {
            case "PRESIDENT" -> PRESIDENT;
            case "VICEPRESIDENT", "DEPUTYPRESIDENT", "DEPUTYCHAIRMAN" -> VICE_PRESIDENT;
            case "SECRETARY" -> SECRETARY;
            case "DEPUTYSECRETARY" -> DEPUTY_SECRETARY;
            case "TREASURER" -> TREASURER;
            case "PRO" -> PRO;
            default -> UNKNOWN;
        };
    }

    public static boolean canEditSocietyProfile(String storedValue) {
        return fromStoredValue(storedValue).profileEditor;
    }

    public static int profileOrder(String storedValue) {
        return fromStoredValue(storedValue).profileOrder;
    }
}
