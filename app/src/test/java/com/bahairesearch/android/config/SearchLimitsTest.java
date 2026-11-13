package com.bahairesearch.android.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The result limit must stay inside the range the settings slider exposes.
 */
public class SearchLimitsTest {

    @Test
    public void clampsBelowRange() {
        assertEquals(SearchLimits.MIN_MAX_QUOTES, SearchLimits.clampMaxQuotes(0));
        assertEquals(SearchLimits.MIN_MAX_QUOTES, SearchLimits.clampMaxQuotes(-25));
    }

    @Test
    public void clampsAboveRange() {
        assertEquals(SearchLimits.MAX_MAX_QUOTES, SearchLimits.clampMaxQuotes(51));
        assertEquals(SearchLimits.MAX_MAX_QUOTES, SearchLimits.clampMaxQuotes(5000));
    }

    @Test
    public void keepsValuesInsideRange() {
        assertEquals(3, SearchLimits.clampMaxQuotes(3));
        assertEquals(12, SearchLimits.clampMaxQuotes(12));
        assertEquals(50, SearchLimits.clampMaxQuotes(50));
    }

    @Test
    public void defaultIsInsideRange() {
        assertEquals(SearchLimits.DEFAULT_MAX_QUOTES,
                SearchLimits.clampMaxQuotes(SearchLimits.DEFAULT_MAX_QUOTES));
    }
}
