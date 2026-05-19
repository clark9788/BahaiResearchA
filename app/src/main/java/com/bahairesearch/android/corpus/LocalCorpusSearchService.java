package com.bahairesearch.android.corpus;

import android.database.Cursor;
import io.requery.android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.bahairesearch.android.config.AppConfig;
import com.bahairesearch.common.model.CorpusSearchHit;
import com.bahairesearch.common.model.QuoteResult;
import com.bahairesearch.common.model.ResearchReport;
import com.bahairesearch.common.search.SearchCore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Full-text search against the corpus database: FTS5 retrieval, filtering, ranking, and deduplication.
 * Pure search logic is delegated to SearchCore; this class owns only the Android SQLite access layer.
 */
public final class LocalCorpusSearchService {

    private static final String TAG = "Corpus";

    private static final class HitsResult {
        final List<CorpusSearchHit> hits;
        final String effectiveQuery;
        final boolean usedFallback;
        HitsResult(List<CorpusSearchHit> hits, String effectiveQuery, boolean usedFallback) {
            this.hits = hits;
            this.effectiveQuery = effectiveQuery;
            this.usedFallback = usedFallback;
        }
    }

    private LocalCorpusSearchService() {}

    /**
     * Searches the corpus for the given topic with no author or title filter.
     */
    public static ResearchReport search(SQLiteDatabase db, String topic, AppConfig appConfig) {
        return search(db, topic, null, null, appConfig);
    }

    /**
     * Searches the corpus for the given topic, optionally scoped to a specific author and title.
     */
    public static ResearchReport search(
            SQLiteDatabase db,
            String topic,
            String explicitAuthor,
            String explicitTitle,
            AppConfig appConfig
    ) {
        String requiredAuthor = explicitAuthor;

        String nearQuery  = SearchCore.toFtsQueryNear(topic, requiredAuthor);
        String ftsQuery   = SearchCore.toFtsQuery(topic, requiredAuthor);
        String orFtsQuery = SearchCore.toFtsQueryOr(topic, requiredAuthor);
        if (ftsQuery.trim().isEmpty()) {
            return new ResearchReport(appConfig.noResultsText(), Collections.emptyList());
        }

        int requestedQuotes   = Math.max(1, appConfig.maxQuotes());
        int retrievalPoolSize = Math.max(requestedQuotes * 12, 60);

        List<String> requestedBookTokens = SearchCore.bookTokensFromTitle(explicitTitle);
        List<String> conceptTerms        = SearchCore.extractContentTerms(topic, requiredAuthor);

        HitsResult hitsResult = findHits(db, nearQuery, ftsQuery, orFtsQuery, retrievalPoolSize,
                requiredAuthor, explicitTitle, requestedBookTokens, appConfig);
        List<CorpusSearchHit> hits = hitsResult.hits;
        logCount(appConfig, "hits", hits.size());

        List<CorpusSearchHit> filtered   = SearchCore.filterByRequestedAuthor(requiredAuthor, hits);
        List<CorpusSearchHit> bookScoped = SearchCore.filterByRequestedBook(filtered, requestedBookTokens);
        List<CorpusSearchHit> topical    = SearchCore.filterByContentTerms(bookScoped, conceptTerms);

        List<String> topicFtsTokens = SearchCore.extractFtsTokens(topic, requiredAuthor);
        List<CorpusSearchHit> combinedPhraseHits = new ArrayList<>();
        boolean nearFired = hitsResult.effectiveQuery.startsWith("NEAR(");
        if (topicFtsTokens.size() >= 2 && !nearFired) {
            combinedPhraseHits.addAll(fetchPhraseHits(db, topic, retrievalPoolSize,
                    requiredAuthor, explicitTitle, requestedBookTokens));
            logCount(appConfig, "phrase hits", combinedPhraseHits.size());
        }
        topical = SearchCore.mergeHits(combinedPhraseHits, topical);
        logCount(appConfig, "after phrase merge", topical.size());

        if (!requestedBookTokens.isEmpty() && topical.size() < requestedQuotes && !nearFired) {
            List<CorpusSearchHit> additional = findAdditionalBookScopedHits(
                    db, requiredAuthor, explicitTitle, requestedBookTokens, conceptTerms,
                    Math.max(240, requestedQuotes * 50));
            topical = SearchCore.mergeHits(topical, additional);
        }

        List<CorpusSearchHit> candidatePool =
                SearchCore.rankForDisplay(SearchCore.removeBoilerplateAndDuplicates(topical));
        logCount(appConfig, "candidatePool", candidatePool.size());

        List<CorpusSearchHit> curated = candidatePool.stream()
                .limit(requestedQuotes)
                .collect(Collectors.toList());
        if (curated.isEmpty()) {
            return new ResearchReport(appConfig.noResultsText(), Collections.emptyList());
        }

        List<QuoteResult> quotes = new ArrayList<>();
        for (CorpusSearchHit hit : curated) {
            quotes.add(new QuoteResult(
                    hit.quote(),
                    SearchCore.blankToFallback(hit.author(), "Unknown"),
                    SearchCore.blankToFallback(hit.title(), "Untitled"),
                    SearchCore.blankToFallback(hit.locator(), "Not specified"),
                    SearchCore.blankToFallback(hit.sourceUrl(), "N/A")));
        }

        String displayQuery = hitsResult.effectiveQuery
                .replaceAll("NEAR\\(([^,]+),\\s*\\d+\\)", "$1")
                .replace("*", "")
                .replace(" AND ", " and ")
                .replace(" OR ", " or ");
        String summary = "Found " + quotes.size() + " passage(s) — searched: " + displayQuery;
        if (hitsResult.usedFallback) {
            summary += "  (Tip: try fewer or more specific keywords)";
        }
        return new ResearchReport(summary, quotes);
    }

    // -------------------------------------------------------------------------
    // SQL query builders
    // -------------------------------------------------------------------------

    private static String buildHitsSql(boolean authorScoped, boolean titleScoped) {
        String authorClause = authorScoped ? "  AND lower(d.author) = lower(?)\n" : "";
        String titleClause  = titleScoped  ? "  AND lower(d.title)  = lower(?)\n" : "";
        return "SELECT\n"
                + "    p.text_content,\n"
                + "    d.author,\n"
                + "    d.title,\n"
                + "    p.locator,\n"
                + "    d.canonical_url,\n"
                + "    bm25(passages_fts) AS score\n"
                + "FROM passages_fts\n"
                + "JOIN passages p ON p.passage_id = passages_fts.rowid\n"
                + "JOIN documents d ON d.doc_id = p.doc_id\n"
                + "WHERE passages_fts MATCH ?\n"
                + authorClause + titleClause
                + "ORDER BY score\n"
                + "LIMIT ?\n";
    }

    private static String buildPhraseSql(boolean authorScoped, boolean titleScoped) {
        String authorClause = authorScoped ? "  AND lower(d.author) = lower(?)\n" : "";
        String titleClause  = titleScoped  ? "  AND lower(d.title)  = lower(?)\n" : "";
        return "SELECT\n"
                + "    p.text_content,\n"
                + "    d.author,\n"
                + "    d.title,\n"
                + "    p.locator,\n"
                + "    d.canonical_url,\n"
                + "    -99999.0 AS score\n"
                + "FROM passages p\n"
                + "JOIN documents d ON d.doc_id = p.doc_id\n"
                + "WHERE lower(p.text_content) LIKE ?\n"
                + authorClause + titleClause
                + "LIMIT ?\n";
    }

    private static String buildBookScopedSql(boolean authorScoped, boolean titleScoped) {
        String authorClause = authorScoped ? "  AND lower(d.author) = lower(?)\n" : "";
        String titleClause  = titleScoped  ? "  AND lower(d.title)  = lower(?)\n" : "";
        return "SELECT\n"
                + "    p.text_content,\n"
                + "    d.author,\n"
                + "    d.title,\n"
                + "    p.locator,\n"
                + "    d.canonical_url,\n"
                + "    -99998.0 AS score\n"
                + "FROM passages p\n"
                + "JOIN documents d ON d.doc_id = p.doc_id\n"
                + "WHERE 1=1\n"
                + authorClause + titleClause
                + "LIMIT ?\n";
    }

    // -------------------------------------------------------------------------
    // Core search — findHits with NEAR/AND/OR fallback
    // -------------------------------------------------------------------------

    private static HitsResult findHits(
            SQLiteDatabase db, String nearQuery, String ftsQuery, String orFtsQuery, int limit,
            String requiredAuthor, String explicitTitle,
            List<String> requestedBookTokens, AppConfig appConfig) {

        boolean authorScoped = !SearchCore.isEmpty(requiredAuthor);
        boolean titleScoped  = !SearchCore.isEmpty(explicitTitle);
        String sql = buildHitsSql(authorScoped, titleScoped);

        List<CorpusSearchHit> hits = Collections.emptyList();
        boolean usedOrFallback = false;
        String usedQuery = ftsQuery;
        boolean nearAttempted = false;

        if (!SearchCore.isEmpty(nearQuery)) {
            nearAttempted = true;

            logCount(appConfig, "FtsQuery NEAR: " + nearQuery + " ->", 0);
            List<CorpusSearchHit> nearHits = executeHitsQuery(db, sql, nearQuery,
                    authorScoped, requiredAuthor, titleScoped, explicitTitle, limit);
            logCount(appConfig, "NEAR hits", nearHits.size());

            logCount(appConfig, "FtsQuery AND (supplement): " + ftsQuery + " ->", 0);
            List<CorpusSearchHit> andHits = executeHitsQuery(db, sql, ftsQuery,
                    authorScoped, requiredAuthor, titleScoped, explicitTitle, limit);
            logCount(appConfig, "AND supplement hits", andHits.size());

            if (!nearHits.isEmpty()) {
                nearHits = SearchCore.applyNearBoost(nearHits);
            }

            hits = SearchCore.mergeHits(nearHits, andHits);

            if (!nearHits.isEmpty()) {
                usedQuery = nearQuery;
            } else if (!andHits.isEmpty()) {
                usedQuery = ftsQuery;
            }
        }

        if (!nearAttempted) {
            logCount(appConfig, "FtsQuery AND: " + ftsQuery + " ->", 0);
            hits = executeHitsQuery(db, sql, ftsQuery,
                    authorScoped, requiredAuthor, titleScoped, explicitTitle, limit);
            logCount(appConfig, "AND hits", hits.size());

            if (hits.isEmpty() && !SearchCore.isEmpty(orFtsQuery) && !orFtsQuery.equals(ftsQuery)) {
                logCount(appConfig, "FtsQuery OR: " + orFtsQuery + " ->", 0);
                hits = executeHitsQuery(db, sql, orFtsQuery,
                        authorScoped, requiredAuthor, titleScoped, explicitTitle, limit);
                logCount(appConfig, "OR hits", hits.size());
                usedOrFallback = true;
                usedQuery = orFtsQuery;
            }
        }

        List<CorpusSearchHit> limited = hits.stream()
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
        return new HitsResult(limited, usedQuery, usedOrFallback);
    }

    private static List<CorpusSearchHit> executeHitsQuery(
            SQLiteDatabase db, String sql, String ftsQuery,
            boolean authorScoped, String requiredAuthor,
            boolean titleScoped, String explicitTitle, int limit) {

        List<String> args = new ArrayList<>();
        args.add(ftsQuery);
        if (authorScoped) args.add(requiredAuthor);
        if (titleScoped)  args.add(explicitTitle);
        args.add(String.valueOf(Math.max(1, limit)));

        List<CorpusSearchHit> hits = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, args.toArray(new String[0]))) {
            while (cursor.moveToNext()) {
                hits.add(new CorpusSearchHit(
                        trimToEmpty(cursor.getString(0)),
                        trimToEmpty(cursor.getString(1)),
                        trimToEmpty(cursor.getString(2)),
                        trimToEmpty(cursor.getString(3)),
                        trimToEmpty(cursor.getString(4)),
                        cursor.getDouble(5)));
            }
        }
        return hits;
    }

    private static List<CorpusSearchHit> fetchPhraseHits(
            SQLiteDatabase db, String knownPhrase, int limit,
            String requiredAuthor, String explicitTitle,
            List<String> requestedBookTokens) {

        boolean authorScoped = !SearchCore.isEmpty(requiredAuthor);
        boolean titleScoped  = !SearchCore.isEmpty(explicitTitle);
        String sql = buildPhraseSql(authorScoped, titleScoped);

        List<String> args = new ArrayList<>();
        args.add("%" + SearchCore.normalizeForMatch(knownPhrase).replace(" ", "%") + "%");
        if (authorScoped) args.add(requiredAuthor);
        if (titleScoped)  args.add(explicitTitle);
        args.add(String.valueOf(Math.max(1, limit)));

        List<CorpusSearchHit> hits = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, args.toArray(new String[0]))) {
            while (cursor.moveToNext()) {
                hits.add(new CorpusSearchHit(
                        trimToEmpty(cursor.getString(0)),
                        trimToEmpty(cursor.getString(1)),
                        trimToEmpty(cursor.getString(2)),
                        trimToEmpty(cursor.getString(3)),
                        trimToEmpty(cursor.getString(4)),
                        cursor.getDouble(5)));
            }
        }
        if (!requestedBookTokens.isEmpty()) {
            return SearchCore.filterByRequestedBook(hits, requestedBookTokens);
        }
        return hits;
    }

    private static List<CorpusSearchHit> findAdditionalBookScopedHits(
            SQLiteDatabase db, String requiredAuthor, String explicitTitle,
            List<String> requestedBookTokens, List<String> contentTerms, int limit) {

        boolean authorScoped = !SearchCore.isEmpty(requiredAuthor);
        boolean titleScoped  = !SearchCore.isEmpty(explicitTitle);
        String sql = buildBookScopedSql(authorScoped, titleScoped);

        List<String> args = new ArrayList<>();
        if (authorScoped) args.add(requiredAuthor);
        if (titleScoped)  args.add(explicitTitle);
        args.add(String.valueOf(Math.max(1, limit)));

        List<CorpusSearchHit> hits = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, args.toArray(new String[0]))) {
            while (cursor.moveToNext()) {
                CorpusSearchHit hit = new CorpusSearchHit(
                        trimToEmpty(cursor.getString(0)),
                        trimToEmpty(cursor.getString(1)),
                        trimToEmpty(cursor.getString(2)),
                        trimToEmpty(cursor.getString(3)),
                        trimToEmpty(cursor.getString(4)),
                        cursor.getDouble(5));
                if (SearchCore.countBookTokenMatches(hit, requestedBookTokens) == 0) continue;
                if (!contentTerms.isEmpty() && !SearchCore.containsAnyContentTerm(hit.quote(), contentTerms)) continue;
                hits.add(hit);
            }
        }
        return hits;
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static void logCount(AppConfig appConfig, String label, int count) {
        if (appConfig.debugIntent()) {
            Log.i(TAG, "[Pipeline] " + label + "=" + count);
        }
    }
}
