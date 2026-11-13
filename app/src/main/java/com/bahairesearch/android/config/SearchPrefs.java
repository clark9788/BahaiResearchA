package com.bahairesearch.android.config;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists the user's search preferences, currently the maximum number of passages returned.
 */
public final class SearchPrefs {

    private static final String PREFS_NAME = "search_prefs";
    private static final String KEY_MAX_QUOTES = "max_quotes";

    private SearchPrefs() {}

    /**
     * Returns the saved result limit, or the default when none has been stored.
     */
    public static int getMaxQuotes(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return SearchLimits.clampMaxQuotes(
                prefs.getInt(KEY_MAX_QUOTES, SearchLimits.DEFAULT_MAX_QUOTES));
    }

    /**
     * Stores the result limit, clamped to the supported range.
     */
    public static void setMaxQuotes(Context context, int maxQuotes) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_MAX_QUOTES, SearchLimits.clampMaxQuotes(maxQuotes)).apply();
    }
}
