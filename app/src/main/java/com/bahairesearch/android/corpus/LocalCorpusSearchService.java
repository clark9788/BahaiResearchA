package com.bahairesearch.android.corpus;

import android.database.Cursor;
import io.requery.android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.bahairesearch.android.config.AppConfig;
import com.bahairesearch.android.model.QuoteResult;
import com.bahairesearch.android.model.ResearchReport;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Full-text search against the corpus database: FTS5 retrieval, filtering, ranking, and deduplication.
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

    private static final int NEAR_DISTANCE = 15;

    private static final Set<String> NOISE_TOKENS = new HashSet<>(Arrays.asList(
            "by", "for", "with", "and", "the", "from", "about",
            "quotes", "quote", "please", "show", "find"));
    private static final Set<String> GENERIC_QUERY_TOKENS = new HashSet<>(Arrays.asList(
            "book", "books", "most", "issue", "issues"));

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

        String nearQuery  = toFtsQueryNear(topic, requiredAuthor);
        String ftsQuery   = toFtsQuery(topic, requiredAuthor);
        String orFtsQuery = toFtsQueryOr(topic, requiredAuthor);
        if (ftsQuery.trim().isEmpty()) {
            return new ResearchReport(appConfig.noResultsText(), Collections.emptyList());
        }

        int requestedQuotes  = Math.max(1, appConfig.maxQuotes());
        int retrievalPoolSize = Math.max(requestedQuotes * 12, 60);

        List<String> requestedBookTokens = bookTokensFromTitle(explicitTitle);
        List<String> conceptTerms        = extractContentTerms(topic, requiredAuthor);

        HitsResult hitsResult = findHits(db, nearQuery, ftsQuery, orFtsQuery, retrievalPoolSize,
                requiredAuthor, explicitTitle, requestedBookTokens, appConfig);
        List<CorpusSearchHit> hits = hitsResult.hits;
        logCount(appConfig, "hits", hits.size());

        List<CorpusSearchHit> filtered  = filterByRequestedAuthor(requiredAuthor, hits);
        List<CorpusSearchHit> bookScoped = filterByRequestedBook(filtered, requestedBookTokens);
        List<CorpusSearchHit> topical   = filterByContentTerms(bookScoped, conceptTerms);

        List<String> topicFtsTokens = extractFtsTokens(topic, requiredAuthor);
        List<CorpusSearchHit> combinedPhraseHits = new ArrayList<>();
        boolean nearFired = hitsResult.effectiveQuery.startsWith("NEAR(");
        if (topicFtsTokens.size() >= 2 && !nearFired) {
            combinedPhraseHits.addAll(fetchPhraseHits(db, topic, retrievalPoolSize,
                    requiredAuthor, explicitTitle, requestedBookTokens));
            logCount(appConfig, "phrase hits", combinedPhraseHits.size());
        }
        topical = mergeHits(combinedPhraseHits, topical);
        logCount(appConfig, "after phrase merge", topical.size());

        if (!requestedBookTokens.isEmpty() && topical.size() < requestedQuotes) {
            List<CorpusSearchHit> additional = findAdditionalBookScopedHits(
                    db, requiredAuthor, explicitTitle, requestedBookTokens, conceptTerms,
                    Math.max(240, requestedQuotes * 50));
            topical = mergeHits(topical, additional);
        }

        List<CorpusSearchHit> candidatePool =
                rankForDisplay(removeBoilerplateAndDuplicates(topical));
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
                    blankToFallback(hit.author(), "Unknown"),
                    blankToFallback(hit.title(), "Untitled"),
                    blankToFallback(hit.locator(), "Not specified"),
                    blankToFallback(hit.sourceUrl(), "N/A")));
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

        boolean authorScoped = !isEmpty(requiredAuthor);
        boolean titleScoped  = !isEmpty(explicitTitle);
        String sql = buildHitsSql(authorScoped, titleScoped);

        List<CorpusSearchHit> hits = Collections.emptyList();
        String usedQuery = ftsQuery;

        if (!isEmpty(nearQuery)) {
            logCount(appConfig, "FtsQuery NEAR: " + nearQuery + " ->", 0);
            hits = executeHitsQuery(db, sql, nearQuery,
                    authorScoped, requiredAuthor, titleScoped, explicitTitle, limit);
            logCount(appConfig, "NEAR hits", hits.size());
            if (!hits.isEmpty()) usedQuery = nearQuery;
        }

        if (hits.isEmpty()) {
            logCount(appConfig, "FtsQuery AND: " + ftsQuery + " ->", 0);
            hits = executeHitsQuery(db, sql, ftsQuery,
                    authorScoped, requiredAuthor, titleScoped, explicitTitle, limit);
            logCount(appConfig, "AND hits", hits.size());
        }

        boolean usedOrFallback = false;
        if (hits.isEmpty() && !isEmpty(orFtsQuery) && !orFtsQuery.equals(ftsQuery)) {
            logCount(appConfig, "FtsQuery OR: " + orFtsQuery + " ->", 0);
            hits = executeHitsQuery(db, sql, orFtsQuery,
                    authorScoped, requiredAuthor, titleScoped, explicitTitle, limit);
            logCount(appConfig, "OR hits", hits.size());
            usedOrFallback = true;
            usedQuery = orFtsQuery;
        }

        if (!requestedBookTokens.isEmpty()) {
            hits = filterByRequestedBook(hits, requestedBookTokens);
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

        boolean authorScoped = !isEmpty(requiredAuthor);
        boolean titleScoped  = !isEmpty(explicitTitle);
        String sql = buildPhraseSql(authorScoped, titleScoped);

        List<String> args = new ArrayList<>();
        args.add("%" + normalizeForMatch(knownPhrase).replace(" ", "%") + "%");
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
            return filterByRequestedBook(hits, requestedBookTokens);
        }
        return hits;
    }

    private static List<CorpusSearchHit> findAdditionalBookScopedHits(
            SQLiteDatabase db, String requiredAuthor, String explicitTitle,
            List<String> requestedBookTokens, List<String> contentTerms, int limit) {

        boolean authorScoped = !isEmpty(requiredAuthor);
        boolean titleScoped  = !isEmpty(explicitTitle);
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
                if (countBookTokenMatches(hit, requestedBookTokens) == 0) continue;
                if (!contentTerms.isEmpty() && !containsAnyContentTerm(hit.quote(), contentTerms)) continue;
                hits.add(hit);
            }
        }
        return hits;
    }

    private static List<CorpusSearchHit> mergeHits(
            List<CorpusSearchHit> primary, List<CorpusSearchHit> secondary) {
        List<CorpusSearchHit> merged = new ArrayList<>(primary);
        Set<String> seen = new HashSet<>();
        for (CorpusSearchHit hit : primary) {
            seen.add(normalizeForMatch(hit.quote()) + "|" + normalizeForMatch(hit.sourceUrl()));
        }
        for (CorpusSearchHit hit : secondary) {
            String key = normalizeForMatch(hit.quote()) + "|" + normalizeForMatch(hit.sourceUrl());
            if (seen.add(key)) merged.add(hit);
        }
        return merged;
    }

    // -------------------------------------------------------------------------
    // Post-retrieval filters
    // -------------------------------------------------------------------------

    private static List<CorpusSearchHit> filterByRequestedAuthor(
            String requiredAuthor, List<CorpusSearchHit> hits) {
        if (isEmpty(requiredAuthor)) return hits;
        String normalized = normalizeForMatch(requiredAuthor);
        return hits.stream()
                .filter(hit -> normalizeForMatch(hit.author()).equals(normalized))
                .collect(Collectors.toList());
    }

    private static List<CorpusSearchHit> filterByContentTerms(
            List<CorpusSearchHit> hits, List<String> contentTerms) {
        if (contentTerms.isEmpty()) return hits;
        return hits.stream()
                .filter(hit -> containsAnyContentTerm(hit.quote(), contentTerms))
                .collect(Collectors.toList());
    }

    private static List<CorpusSearchHit> filterByRequestedBook(
            List<CorpusSearchHit> hits, List<String> requestedBookTokens) {
        if (requestedBookTokens.isEmpty()) return hits;
        int requiredMatches = requestedBookTokens.size() <= 2 ? requestedBookTokens.size() : 2;
        return hits.stream()
                .filter(hit -> countBookTokenMatches(hit, requestedBookTokens) >= requiredMatches)
                .collect(Collectors.toList());
    }

    private static int countBookTokenMatches(CorpusSearchHit hit, List<String> requestedBookTokens) {
        String normalizedTitle = normalizeForMatch(hit.title());
        String normalizedUrl   = normalizeForMatch(hit.sourceUrl());
        int matches = 0;
        for (String token : requestedBookTokens) {
            if (normalizedTitle.contains(token) || normalizedUrl.contains(token)) matches++;
        }
        return matches;
    }

    private static boolean containsAnyContentTerm(String quote, List<String> contentTerms) {
        String normalizedQuote = normalizeForMatch(quote);
        Set<String> quoteTokens = new HashSet<>();
        for (String token : normalizedQuote.split("\\s+")) {
            if (!token.isEmpty()) quoteTokens.add(token);
        }
        for (String term : contentTerms) {
            if (quoteTokens.contains(term)) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Ranking and boilerplate removal
    // -------------------------------------------------------------------------

    private static List<CorpusSearchHit> removeBoilerplateAndDuplicates(List<CorpusSearchHit> hits) {
        List<CorpusSearchHit> curated = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CorpusSearchHit hit : hits) {
            if (boilerplateReason(hit) != null) continue;
            String key = normalizeForMatch(hit.quote());
            if (key.isEmpty() || !seen.add(key)) continue;
            curated.add(hit);
        }
        return curated;
    }

    private static List<CorpusSearchHit> rankForDisplay(List<CorpusSearchHit> hits) {
        return hits.stream()
                .sorted((l, r) -> {
                    boolean lPhrase = l.score() <= -99990;
                    boolean rPhrase = r.score() <= -99990;
                    if (lPhrase != rPhrase) return lPhrase ? -1 : 1;
                    if (lPhrase) {
                        int lLen = l.quote() == null ? 0 : l.quote().length();
                        int rLen = r.quote() == null ? 0 : r.quote().length();
                        return Integer.compare(lLen, rLen);
                    }
                    int lb = qualityBand(l.quote());
                    int rb = qualityBand(r.quote());
                    if (lb != rb) return Integer.compare(lb, rb);
                    return Double.compare(l.score(), r.score());
                })
                .collect(Collectors.toList());
    }

    private static int qualityBand(String quote) {
        int length = quote == null ? 0 : quote.trim().length();
        if (length >= 200 && length <= 900)  return 0;
        if (length >= 120 && length <= 1100) return 1;
        return 2;
    }

    private static String boilerplateReason(CorpusSearchHit hit) {
        if (hit.quote().length() > 15_000) return "too-long";
        String q = normalizeForMatch(hit.quote());
        if (q.contains("bahai reference library"))                                return "bahai-ref-lib";
        if (q.startsWith("a collection of") || q.startsWith("a selection of"))    return "collection-header";
        if (q.contains("can be found here"))                                       return "found-here";
        if (q.contains("downloads about downloads")
                || q.contains("all downloads in authoritative writings and guidance")
                || q.contains("copyright and terms of use")
                || q.contains("read online")
                || q.contains("bahai org home")
                || q.contains("search the bahai reference library"))               return "nav-element";
        if (q.contains("see also"))                                                return "see-also";
        return null;
    }

    // -------------------------------------------------------------------------
    // Term and concept inference
    // -------------------------------------------------------------------------

    private static List<String> extractContentTerms(String topic, String requiredAuthor) {
        String normalizedTopic = normalizeForMatch(topic);
        if (normalizedTopic.isEmpty()) return Collections.emptyList();
        Set<String> authorTerms = new HashSet<>();
        if (!isEmpty(requiredAuthor)) {
            for (String token : normalizeForMatch(requiredAuthor).split("\\s+")) {
                if (!token.isEmpty()) authorTerms.add(token);
            }
        }
        List<String> terms = new ArrayList<>();
        for (String token : normalizedTopic.split("\\s+")) {
            if (token.length() < 4) continue;
            if (NOISE_TOKENS.contains(token) || GENERIC_QUERY_TOKENS.contains(token)
                    || authorTerms.contains(token)) continue;
            terms.add(token);
        }
        return terms;
    }

    private static List<String> bookTokensFromTitle(String explicitTitle) {
        if (isEmpty(explicitTitle)) return Collections.emptyList();
        List<String> tokens = new ArrayList<>();
        for (String token : normalizeForMatch(explicitTitle).split("\\s+")) {
            if (token.length() >= 3 && !NOISE_TOKENS.contains(token)
                    && !GENERIC_QUERY_TOKENS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    // -------------------------------------------------------------------------
    // FTS query building
    // -------------------------------------------------------------------------

    private static String toFtsQueryNear(String topic, String resolvedAuthor) {
        List<String> tokens = extractFtsTokens(topic, resolvedAuthor);
        if (tokens.size() != 2) return "";
        return "NEAR(" + tokens.get(0) + " " + tokens.get(1) + ", " + NEAR_DISTANCE + ")";
    }

    private static String toFtsQuery(String topic, String resolvedAuthor) {
        List<String> tokens = extractFtsTokens(topic, resolvedAuthor);
        return tokens.isEmpty() ? "" : buildAndQuery(tokens);
    }

    private static String toFtsQueryOr(String topic, String resolvedAuthor) {
        List<String> tokens = extractFtsTokens(topic, resolvedAuthor);
        if (tokens.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(" OR ");
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }

    private static List<String> extractFtsTokens(String topic, String resolvedAuthor) {
        if (topic == null) return Collections.emptyList();
        Set<String> authorTokens = buildAuthorTokenSet(resolvedAuthor);
        List<String> tokens = new ArrayList<>();
        for (String token : topic.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+")) {
            String trimmed = token.trim();
            if (trimmed.length() >= 3 && !NOISE_TOKENS.contains(trimmed)
                    && !authorTokens.contains(trimmed)) {
                tokens.add(trimmed + "*");
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(tokens));
    }

    private static String buildAndQuery(List<String> tokens) {
        if (tokens.size() <= 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tokens.size(); i++) {
                if (i > 0) sb.append(" AND ");
                sb.append(tokens.get(i));
            }
            return sb.toString();
        }
        List<String> required = tokens.subList(0, 3);
        List<String> optional = tokens.subList(3, tokens.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < required.size(); i++) {
            if (i > 0) sb.append(" AND ");
            sb.append(required.get(i));
        }
        sb.append(" AND (");
        for (int i = 0; i < optional.size(); i++) {
            if (i > 0) sb.append(" OR ");
            sb.append(optional.get(i));
        }
        sb.append(")");
        return sb.toString();
    }

    private static Set<String> buildAuthorTokenSet(String resolvedAuthor) {
        if (isEmpty(resolvedAuthor)) return Collections.emptySet();
        Set<String> tokens = new HashSet<>();
        for (String token : normalizeForMatch(resolvedAuthor).split("\\s+")) {
            if (!token.isEmpty()) tokens.add(token);
        }
        return tokens;
    }

    // -------------------------------------------------------------------------
    // Normalization utilities
    // -------------------------------------------------------------------------

    private static String normalizeForMatch(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void logCount(AppConfig appConfig, String label, int count) {
        if (appConfig.debugIntent()) {
            Log.i(TAG, "[Pipeline] " + label + "=" + count);
        }
    }
}
