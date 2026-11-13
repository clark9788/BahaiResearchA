package com.bahairesearch.android.config;

/**
 * Bounds and defaults for the number of passages a search may return.
 *
 * <p>Kept free of Android dependencies so the clamping logic can be unit tested on the JVM.
 */
public final class SearchLimits {

    public static final int DEFAULT_MAX_QUOTES = 12;
    public static final int MIN_MAX_QUOTES = 3;
    public static final int MAX_MAX_QUOTES = 50;

    private SearchLimits() {}

    /**
     * Constrains a requested result count to the supported range.
     */
    public static int clampMaxQuotes(int maxQuotes) {
        return Math.max(MIN_MAX_QUOTES, Math.min(MAX_MAX_QUOTES, maxQuotes));
    }
}
