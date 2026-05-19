 # Search Logic — Bahá'í Research

This document describes the full search pipeline in `LocalCorpusSearchService.java` Part of this logic has been to a separate project BahaiSearchCommon in `SourceCore.java`. It is intended as a reference for understanding, testing, and improving search quality.

---

## Overview

The search pipeline runs entirely offline against `corpus.db`, a bundled SQLite database containing ~22,000 passages drawn from authoritative Bahá'í writings. The database has three relevant tables:

- `passages` — the raw text passages (`passage_id`, `doc_id`, `text_content`, `locator`)
- `documents` — metadata per document (`doc_id`, `author`, `title`, `canonical_url`)
- `passages_fts` — FTS5 virtual table, content-table backed by `passages`, indexed on `text_content`

The pipeline has six stages:

```
Input → Tokenize & Build FTS Query → FTS5 Retrieval (NEAR+AND merge → OR fallback)
     → Post-Retrieval Filtering → Phrase Merge → Deduplication & Ranking → Output
```

---

## Stage 1: Input Normalization

All text matching throughout the pipeline runs through `normalizeForMatch()`:

1. Unicode decomposition (NFD) — strips accents and diacritics (ā → a, é → e)
2. Lowercase
3. Strip all non-alphanumeric characters, collapse to single spaces

This means `Bahá'u'lláh`, `Baha'u'llah`, and `bahaullah` all normalize to `baha u llah` and match identically. The same normalization is applied to query text, passage text, author names, and title names before any comparison.

**Note:** FTS token extraction now also uses `normalizeForMatch()` so that diacritics are stripped *before* building FTS5 queries. This ensures that a search for `"Bahá'u'lláh"` generates the same FTS token as `"bahaullah"` — consistent with every other pipeline comparison.

---

## Stage 2: Author Resolution

The required author comes directly from the author spinner. If the user selects "All Authors", `requiredAuthor` is null and the search is corpus-wide. If an author is selected, their name tokens are excluded from FTS query token extraction so that the author name itself does not become a search term and skew results.

---

## Stage 3: FTS Query Building

### Token extraction

`extractFtsTokens()` processes the user's input into a list of FTS5 search tokens:

1. Normalize via `normalizeForMatch()` (NFD → lowercase → strip non-alphanumeric)
2. Discard tokens shorter than 3 characters
3. Discard **noise tokens**: `by for with and the from about quotes quote please show find are but can had has its may not out was all any she who why yet you how let too now`
4. Discard **generic tokens**: `book books most issue issues` (applied in `extractContentTerms`, but tokens are already dropped at extraction)
5. Discard any token that appears in the resolved author's name
6. Append `*` (prefix wildcard) to each remaining token
7. Deduplicate while preserving order

**Example:** `"prayer Baha'u'llah"` with resolved author `Baha'u'llah`
→ tokens after stripping author: `["prayer*"]`

**Example:** `"unity mankind service"`
→ tokens: `["unity*", "mankind*", "service*"]`

### NEAR + AND merged (2–3 token queries)

`toFtsQueryNear()` produces a proximity query when the user enters 2 or 3 search tokens:

```
NEAR(divine* intervention*, NEAR_DISTANCE)
NEAR(unity* mankind* service*, NEAR_DISTANCE)
```

All tokens must appear within 15 words of each other. This prevents false positives where the words match independently in unrelated parts of a long passage.

The pipeline **always runs both NEAR and AND for 2–3 token queries**:

1. NEAR query runs first (proximity — high precision)
2. AND query runs simultaneously (broader recall)
3. NEAR hits receive a score boost (BM25 × 1000) so they sort above AND hits in ranking
4. The two result sets are merged and deduplicated

This ensures that when NEAR returns only a few results, AND fills the gaps — giving the user a full set of results while still prioritizing proximity matches at the top.

### AND query (primary for 3+ tokens)

`buildAndQuery()` produces the FTS5 MATCH expression:

- **1–3 tokens:** all required — `token1* AND token2* AND token3*`
- **4+ tokens:** first 3 required, remaining optional:
  `token1* AND token2* AND token3* AND (token4* OR token5* OR ...)`

This means a 4-word query requires at least the first 3 words to appear in every result, while extra words can appear in any of them.

### OR query (fallback)

`toFtsQueryOr()` joins all tokens with OR: `token1* OR token2* OR token3*`

This is only used if the AND query (or NEAR+AND merge) returns zero results. When the fallback fires, the status bar shows: *"(Tip: try fewer or more specific keywords)"*

---

## Stage 4: FTS5 Retrieval

The primary database query (`buildHitsSql`) uses the FTS5 virtual table:

```sql
SELECT p.text_content, d.author, d.title, p.locator, d.canonical_url,
       bm25(passages_fts) AS score
FROM passages_fts
JOIN passages p ON p.passage_id = passages_fts.rowid
JOIN documents d ON d.doc_id = p.doc_id
WHERE passages_fts MATCH ?
  [AND lower(d.author) = lower(?)]   -- only when author is scoped
  [AND lower(d.title)  = lower(?)]   -- only when title is scoped
ORDER BY score
LIMIT ?
```

`bm25()` is FTS5's built-in relevance ranking function (lower score = more relevant, since BM25 returns negative values here). The retrieval pool size is `max(maxQuotes × 12, 60)` — by default 144 passages — to allow post-retrieval filtering to still leave enough results.

**Note:** The FTS5 library used is `com.github.requery:sqlite-android:3.49.0` via JitPack, not Android's system SQLite, because the standard emulator image in Android Studio does not compile in FTS5.

---

## Stage 5: Post-Retrieval Filtering

After FTS5 retrieval, the hit list passes through three sequential filters:

### 5a. Author filter (`filterByRequestedAuthor`)

If a required author is set, discards any hit whose `author` field does not exactly match (after normalization). This double-checks the SQL WHERE clause, which uses `lower()` comparison but not Unicode normalization.

### 5b. Book filter (`filterByRequestedBook`)

If a title was selected from the title spinner, its words are tokenized (noise words removed, minimum 3 characters) and used as book tokens. Each hit's `title` and `canonical_url` are checked for matches. The threshold is:

- 1–2 book tokens: all must match
- 3+ book tokens: at least 2 must match

This allows partial matches on long book names while still being specific.

### 5c. Content term filter (`filterByContentTerms`)

Discards hits that contain none of the user's concept terms (3+ character non-noise tokens from the query, with noise tokens like `the` `and` `but` `for` excluded). This prevents off-topic results that happened to match a common word. The check is word-exact against the normalized passage text.

---

## Stage 6: Phrase Search Merge

**Skipped when NEAR fired (i.e., effective query starts with "NEAR").** When NEAR returned results, it already found passages where the two tokens are within 15 words of each other — phrase LIKE would only add weaker, distance-unbounded matches. Phrase LIKE runs only when NEAR returned 0 and AND (or OR) fired instead.

When it runs, a separate **LIKE-based phrase search** queries the database (`fetchPhraseHits` / `buildPhraseSql`):

```sql
SELECT p.text_content, d.author, d.title, p.locator, d.canonical_url,
       -99999.0 AS score
FROM passages p
JOIN documents d ON d.doc_id = p.doc_id
WHERE lower(p.text_content) LIKE ?
LIMIT ?
```

The LIKE pattern is built by normalizing the full query and joining tokens with `%`:
`"unity mankind"` → `%unity%mankind%`

This catches passages where the exact normalized sequence of words appears together — useful when the words appear adjacent or nearly adjacent but FTS5 tokenization ranked them poorly.

These phrase hits are assigned a sentinel score of `-99999.0` and are given display priority over FTS5 BM25 hits during ranking (see Stage 7).

The two hit lists are merged using `mergeHits()`, which deduplicates on `(normalized_quote, normalized_url)`.

### Book-scope fallback

If book tokens are present but the combined hit count is still below the requested quote count, a third query (`buildBookScopedSql`) scans all passages under the author/title scope with no keyword filter, then applies the content term check in Java. Score is `-99998.0`. This ensures a title-scoped search always returns something even when keyword matching is sparse.

---

## Stage 7: Boilerplate Removal and Ranking

### Boilerplate removal (`removeBoilerplateAndDuplicates`)

Passages are discarded if they match any of these patterns (normalized text):

| Reason | Pattern |
|---|---|
| `too-long` | passage > 15,000 characters |
| `bahai-ref-lib` | contains `bahai reference library` |
| `collection-header` | starts with `a collection of` or `a selection of` |
| `found-here` | contains `can be found here` |
| `nav-element` | contains navigation/UI strings from the web corpus |
| `see-also` | contains `see also` |

Exact duplicates (same normalized text) are also removed.

### Ranking (`rankForDisplay`)

Hits are sorted by:

1. **Phrase hits first** — score ≤ -99995 (phrase-LIKE sentinel) sorts above all others; within phrase hits, shorter passages rank higher (more precise match)

2. **NEAR-boosted hits** — NEAR proximity hits receive a ×1000 multiplier on their BM25 score, so stronger NEAR matches outrank weaker ones while still sorting above raw BM25 AND/OR hits

3. **BM25 score** — among non-phrase hits, lower (more negative) BM25 score ranks first

Short outliers (< 90 characters) and very long outliers (> 15,000 characters) are filtered out in `boilerplateReason()` before ranking, so the quality-band step is no longer needed.

---

## Configuration (`AppConfig.defaults()`)

| Parameter | Value | Purpose |
|---|---|---|
| `maxQuotes` | 12 | Maximum results returned |
| `debugIntent` | false | Set to true to log pipeline counts to Logcat |
| `noResultsText` | "No results found." | Displayed when pipeline returns empty |

---

## What the User Sees

After the pipeline completes, the summary line shows:

- `Found N passage(s) — searched: <effective query>`
- If OR fallback was used: `(Tip: try fewer or more specific keywords)` appended

The effective query has wildcards stripped and operators lowercased for readability.

---

## Known Strengths

- **NEAR + AND merged** — proximity results get priority, but AND fills gaps when NEAR is thin
- **AND-first with OR fallback** ensures the user always gets something back while preferring precision
- **Prefix wildcards** (`word*`) handle word endings — `pray*` matches pray, prayer, prayers, praying
- **Unicode normalization** makes `Bahá'u'lláh` and `bahaullah` equivalent across FTS and Java filtering
- **Phrase merge** catches exact multi-word sequences that tokenized FTS might rank poorly
- **Boilerplate removal** prevents web-scraped navigation text from appearing as results

---

## Known Limitations and Potential Improvements

### 1. Prefix-only wildcards, no infix
`pray*` matches "prayer" but `*ayer` is not supported by FTS5. Not likely to matter much for this corpus, but worth noting.

### 2. Content term filter can over-filter
`filterByContentTerms` requires an exact whole-word match in the passage. The word `"unity"` will not match `"unification"` even though they are conceptually related. This could be improved with stemming or synonym expansion.

### 3. Phrase LIKE pattern is order-dependent
`%unity%mankind%` matches "unity of mankind" but not "mankind's unity". If word order in the query doesn't match the text, the phrase hit is missed. An order-independent version would require multiple permutations.

### 4. Length floor is hard-coded in boilerplateReason
Passages shorter than 90 characters are now filtered in `boilerplateReason()`. This catches hidden words, footnotes, and Kitáb-i-Aqdas notes. The threshold is hard-coded rather than configurable, so tuning it requires a code change.

### 5. NEAR distance is a fixed constant
The NEAR window is hardcoded at 15 tokens. Too tight misses valid passages with intervening clauses; too loose behaves like AND. 15 is a reasonable default but could be made configurable in `AppConfig` if tuning is needed.

### 6. No search history
Users frequently discover good search terms by iterating. A lightweight search history (last 10 queries stored in SharedPreferences) would save retyping.

### 7. Ranking is universal, not personalized
All users see the same ranking. There is no mechanism to learn from which results a user copies or reads longer. This is by design for an offline app, but noting it as a future possibility.

---

## Debugging

Set `debugIntent = true` in `AppConfig.defaults()` to enable pipeline logging to Logcat under the tag `Corpus`. Each stage logs its hit count, allowing you to see exactly where the pipeline narrows the results.

New log lines added by this version:

| Log tag | What it shows |
|---|---|
| `FtsQuery NEAR: ... ->` | NEAR query being attempted |
| `NEAR hits` | Number of NEAR results |
| `FtsQuery AND (supplement): ... ->` | AND supplement query (always runs alongside NEAR) |
| `AND supplement hits` | Number of AND results |
