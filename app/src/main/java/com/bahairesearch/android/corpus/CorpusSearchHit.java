package com.bahairesearch.android.corpus;

/**
 * Raw FTS5 search hit returned from the database before post-processing and ranking.
 */
final class CorpusSearchHit {
    private final String quote;
    private final String author;
    private final String title;
    private final String locator;
    private final String sourceUrl;
    private final double score;

    CorpusSearchHit(String quote, String author, String title,
                    String locator, String sourceUrl, double score) {
        this.quote = quote;
        this.author = author;
        this.title = title;
        this.locator = locator;
        this.sourceUrl = sourceUrl;
        this.score = score;
    }

    String quote()     { return quote; }
    String author()    { return author; }
    String title()     { return title; }
    String locator()   { return locator; }
    String sourceUrl() { return sourceUrl; }
    double score()     { return score; }
}
