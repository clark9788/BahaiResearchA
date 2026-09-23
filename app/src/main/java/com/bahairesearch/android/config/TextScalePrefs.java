package com.bahairesearch.android.config;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists the reading text scale shared by the results list and the source viewer.
 */
public final class TextScalePrefs {

    private static final String PREFS_NAME = "text_scale_prefs";
    private static final String KEY_TEXT_SCALE = "text_scale";

    /** Bounds keep the text legible while preventing a pinch from rendering it unusable. */
    public static final float MIN_SCALE = 0.75f;
    public static final float MAX_SCALE = 2.5f;
    public static final float DEFAULT_SCALE = 1.0f;

    private TextScalePrefs() {}

    /**
     * Returns the saved text scale, or the default when none has been stored.
     */
    public static float get(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return clamp(prefs.getFloat(KEY_TEXT_SCALE, DEFAULT_SCALE));
    }

    /**
     * Stores the text scale, clamped to the supported range.
     */
    public static void set(Context context, float scale) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putFloat(KEY_TEXT_SCALE, clamp(scale)).apply();
    }

    /**
     * Constrains a scale factor to the supported range.
     */
    public static float clamp(float scale) {
        if (Float.isNaN(scale)) {
            return DEFAULT_SCALE;
        }
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    /**
     * Converts a scale factor to a whole-number percentage (1.0 becomes 100).
     */
    public static int toPercent(float scale) {
        return Math.round(scale * 100f);
    }
}
