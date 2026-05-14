# LocalCorpusSearchService — Improvement Opportunities

This document captures eight observations and potential refinements for the search algorithm in `LocalCorpusSearchService.java`. Each section corresponds to one finding from the algorithm review.

Updated `2026-05-14` — Items 1, 2, 4, 5, 6, 7 are resolved (4 is intentional, 2 was already clean).

---

## 1. NEAR Proximity Limited to Exactly 2 Tokens — ✅ Done

`toFtsQueryNear()` returned an empty string unless the tokenized query contained exactly 2 tokens:

```java
if (tokens.size() != 2) return "";
return "NEAR(" + tokens.get(0) + " " + tokens.get(1) + ", " + NEAR_DISTANCE + ")";
```

For 3+ token queries, NEAR proximity was never attempted — the pipeline jumped straight to AND.

**Resolution (commit `2dff083`):**

Extended `toFtsQueryNear()` to support up to 3 tokens in a single NEAR clause:

```java
// tokens.size() must be >= 2
String joined = String.join(" ", tokens);
return "NEAR(" + joined + ", " + NEAR_DISTANCE + ")";
```

- FTS5's `NEAR(a b c, N)` now fires on 2- and 3-token queries.
- Tested and verified working: 3-word proximity matches surface relevant passages correctly.

---

## 2. `minPassageLength` Configuration — ✅ Already Clean

The `AppConfig` `minPassageLength` field that this item referred to no longer exists in the codebase — it was removed at some point before the `qualityBand()` changes. `boilerplateReason()` enforces passage length directly with hard-coded thresholds (`< 80` and `> 15,000`), and there is no leftover config field to clean up. Nothing to do here.

---

## 3. OR Fallback Is Blocked When a NEAR Query Was Attempted  — Open

The OR fallback only fires when `!nearAttempted` (line 253):

```java
if (!nearAttempted) {
    List<CorpusSearchHit> andHits = ...;
    if (hits.isEmpty() && !isEmpty(orFtsQuery) && !orFtsQuery.equals(ftsQuery)) {
        // OR fallback
    }
}
```

If a NEAR query was attempted (i.e., the topic had exactly 2 tokens), even if NEAR returned zero hits *and* the AND supplement returned zero hits, the OR fallback is skipped entirely.

**Considerations:**

- For 2-token queries, if both NEAR and AND return nothing, the user gets zero results — even though an OR query might have found something relevant.
- Consider restructuring so that OR still fires as a last resort when the NEAR+AND path produces nothing.
- This may be an unintentional bug in the fallback logic path.

---

## 4. Phrase LIKE Matching Is Overly Strict — ✅ Intentional Design

Phrase hits are fetched via SQL `LIKE` after joining tokens with `%`:

```java
args.add("%" + normalizeForMatch(knownPhrase).replace(" ", "%") + "%");
```

This requires all tokens to appear **in order** with zero intervening distance. For example, searching "unity of mankind" will match "unity of mankind" but will miss "...unity ... of ... mankind" where the words appear close but not adjacent.

**Why it's strict by design:** This is the "exact quote" guarantee — it ensures that whatever the user types in quotes will appear verbatim in the returned passage. With the new length-based `boilerplateReason()` filter (< 80 chars), short passages that might have been the only exact-match candidates can get eliminated. The LIKE bypasses all the BM25/NEAR pipelines, making sure the user's exact typed phrase surfaces regardless of length or score considerations.

**Trade-off:** Strict LIKE can miss passages where the tokens appear close but not adjacent (e.g., "unity of all mankind" vs "unity of mankind"). If this becomes a practical problem, possible refinements:
- Use FTS5's `NEAR` operator with a small distance (5–8 tokens) as a softer fallback
- Or generate multiple LIKE patterns for different token-window sizes (bigrams, trigrams)
- But the first question should be: does the strict approach actually miss useful results in practice?

---

## 5. Quality Band Constants Are Hard-Coded and Opinionated  — ✅ Done

The `qualityBand()` method defined three tiers based on passage length:

```java
if (length >= 200 && length <= 900)  return 0;   // best
if (length >= 120 && length <= 1100) return 1;   // acceptable
return 2;                                          // undesirable
```

Passages shorter than 120 characters or longer than 1100 were penalized regardless of relevance. This also overrode all other ranking signals (NEAR, BM25) in `rankForDisplay()`.

**Resolution (commit `e3b0040`):**

- **Removed `qualityBand()` entirely** — the method and its tiered comparator are gone.
- `rankForDisplay()` now proceeds directly: phrase match → BM25 score.
- **Added length filtering to `boilerplateReason()`**:
  - Empty passages (trimmed to "") → filtered
  - Passages `< 90` characters → filtered (catches hidden words, footnotes, Kitáb-i-Aqdas notes)
  - Passages `> 15,000` characters → filtered (outliers)
- Tested and verified: the 90-char floor catches unwanted short snippets effectively.

**Why this approach instead of config:** A hard floor in `boilerplateReason()` is simpler than wiring `minPassageLength` into the pipeline, and the value (90) can be tuned in one place.

---

## 6. Content Term Minimum Length of 4 Excludes Short but Important Words — ✅ Done

`extractContentTerms()` previously skipped tokens shorter than 4 characters:

```java
if (token.length() < 4) continue;
```

In the Bahá'í corpus, this meant 2- and 3-letter content words like "God", "law", "one", "way", and "day" were never used for post-retrieval content-term filtering.

**Resolution:**

- **Lowered threshold** from 4 to 3 — `token.length() < 3`
- **Expanded `NOISE_TOKENS`** with common 3-letter filler words: `are`, `but`, `can`, `had`, `has`, `its`, `may`, `not`, `out`, `was`, `all`, `any`, `she`, `who`, `why`, `yet`, `you`, `how`, `let`, `too`, `now`

Short content words like "God", "law", "one" now participate in content-term filtering. Common 3-letter fillers are excluded to avoid false rejections.

---

## 7. Score Boosts Are Categorical Rather Than Proportional — ✅ Done

Different query types assigned sentinel scores that created strict ranking tiers:

| Strategy      | Score Sentinels       | Rank Position |
|---------------|-----------------------|---------------|
| NEAR          | raw BM25 + (~ -50000) | Highest       |
| Phrase LIKE   | -99999.0              | Second        |
| Book-scoped   | -99998.0              | Third         |
| AND / OR      | raw BM25 (~ -2 to -15)| Lowest        |

Because raw BM25 scores were typically in the range -2 to -15, and NEAR-boosted scores were around -50002 to -50015, a weak NEAR match would **always** outrank a strong AND match regardless of actual relevance.

**Resolution (commit `2dff083`):**

Replaced the additive -50000 boost with a proportional ×1000 multiplier:

```java
// Before: score = bm25Score - 50000
// After:  score = bm25Score * 1000
```

This means a strong BM25 match (e.g., -8) with proximity becomes -8000, while a weak match (e.g., -2) becomes -2000. The proximity signal is preserved but proportional to the underlying relevance. Phrase and book-scoped sentinels (-99999, -99998) still rank highest but are only relevant when no FTS5 results exist for those strategies.

---

## 8. Thread Safety with Requery SQLite Database

The service uses `io.requery.android.database.sqlite.SQLiteDatabase` (Requery's custom Android SQLite). There is no explicit synchronization, and the method signatures assume the caller provides the database handle.

**Considerations:**

- Multiple simultaneous searches on the same `SQLiteDatabase` instance can cause `SQLiteDatabaseLockedException` under concurrent access.
- If searches can be triggered concurrently (e.g., from different Activities, a ViewModel on a background thread, or from search suggestions), ensure each call receives its own database connection, or synchronize access.
- Requery's SQLite wrapper is generally thread-safe for reading, but checking the specific connection management strategy is advisable.
- Consider documenting or enforcing single-threaded usage if concurrent search is not expected.

---

## Implementation Summary

| Item | Status | Commit | Scope |
|---|---|---|---|
| 1. NEAR 2-token limit | ✅ Done | `2dff083` | `toFtsQueryNear()` extended for 3 tokens |
| 2. minPassageLength unused | ✅ Already Clean | — | No leftover config — removed before qualityBand changes |
| 3. OR fallback blocked | Open | — | Logic bug in fallback path |
| 4. Phrase LIKE strict | ✅ Intentional Design | — | Exact quote guarantee — satisfies INE bypass |
| 5. qualityBand constants | ✅ Done | `e3b0040` | Removed, replaced with boilerplate checks (90–15,000) |
| 6. Content term min length | ✅ Done | current | Threshold lowered 4→3, noise set expanded |
| 7. Categorical score boosts | ✅ Done | `2dff083` | -50000 additive → ×1000 proportional |
| 8. Thread safety | Open | — | Requires access pattern review |

*Generated from algorithm review on 2026-05-12. Status updated 2026-05-14.*