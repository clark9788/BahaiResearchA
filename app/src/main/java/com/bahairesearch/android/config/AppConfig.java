package com.bahairesearch.android.config;

/**
 * Immutable configuration for a corpus search session.
 */
public final class AppConfig {
    private final String noResultsText;
    private final boolean debugIntent;
    private final int maxQuotes;
    private final int minPassageLength;

    public AppConfig(String noResultsText, boolean debugIntent, int maxQuotes, int minPassageLength) {
        this.noResultsText = noResultsText;
        this.debugIntent = debugIntent;
        this.maxQuotes = maxQuotes;
        this.minPassageLength = minPassageLength;
    }

    /**
     * Returns the default configuration used for a standard search session.
     */
    public static AppConfig defaults() {
        return new AppConfig("No results found.", false, 12, 80);
    }

    public String noResultsText() { return noResultsText; }
    public boolean debugIntent()  { return debugIntent; }
    public int maxQuotes()        { return maxQuotes; }
    public int minPassageLength() { return minPassageLength; }
}
