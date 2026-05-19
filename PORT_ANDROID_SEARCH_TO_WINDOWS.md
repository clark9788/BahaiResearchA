# Android Search Changes — Porting Guide for Windows Project

Generated 2026-05-17 from the BahaiResearchA Android project (Java).

This document lists the search algorithm changes made in commits up to and including
`90d3f2e` (v1.3.0) that the Windows project may need to absorb.

---

## Project Context

- **Android project:** `d:\AI-Python\BahaiResearchA` — Java, Gradle, Android SDK
- **Windows project:** Separate project (path TBD — user to provide)
- **Shared asset:** `corpus.db` (SQLite, FTS5, ~22K passages) — same database on both platforms
- **Key file:** `app/src/main/java/com/bahairesearch/android/corpus/LocalCorpusSearchService.java`

---

## Change 1: NEAR Proximity Expanded from 2 to 3 Tokens

**Before:** `toFtsQueryNear()` only fired for exactly 2 tokens.
**After:** Fires for 2 or 3 tokens.

```java
// OLD
if (tokens.size() != 2) return "";
return "NEAR(" + tokens.get(0) + " " + tokens.get(1) + ", " + NEAR_DISTANCE + ")";

// NEW
if (tokens.size() < 2 || tokens.size() > 3) return "";
String joined = String.join(" ", tokens);
return "NEAR(" + joined + ", " + NEAR_DISTANCE + ")";
```

**NEAR_DISTANCE** = 15 (unchanged).

**Impact:** 3-word queries ("unity mankind service") now get a NEAR(…) clause instead of
jumping straight to AND. All three words must appear within 15 tokens of each other.

---

## Change 2: qualityBand() Removed — Replaced with Boilerplate Length Checks

**Before:** `qualityBand()` assigned three tiers based on passage length:
- Band 0 (best): 200–900 chars
- Band 1 (acceptable): 120–1100 chars
- Band 2 (penalized): everything else

This overrode BM25 score in ranking and penalized short but relevant passages.

**After:** `qualityBand()` is deleted entirely. `rankForDisplay()` now sorts by:
1. Phrase LIKE hits (score ≤ -99995) — sorted by passage length (shorter = better)
2. BM25 score (lower/more negative = better), with NEAR hits getting ×1000 multiplier

**Length filtering moved to `boilerplateReason()`:**
- Passages < 90 characters → discarded (catches footnotes, hidden words, Kitáb-i-Aqdas notes)
- Empty passages (trimmed to "") → discarded
- Passages > 15,000 characters → discarded

```java
// If the Windows project has a quality-band concept, replace it with these checks:
if (normalized.length() < 90) return "too-short";
if (normalized.length() > 15000) return "too-long";
```

---

## Change 3: Content-Term Threshold Lowered 4 → 3

**Before:** `extractContentTerms()` skipped tokens shorter than 4 characters.
**After:** Skips tokens shorter than 3 characters.

```java
// OLD
if (token.length() < 4) continue;

// NEW
if (token.length() < 3) continue;
```

**Impact:** Short content words like "God", "law", "one", "way", "day", "son" now
participate in content-term filtering. These were previously excluded, meaning a query
containing "God" could match passages that didn't actually contain "God".

---

## Change 4: NOISE_TOKENS Expanded

**Before:**
```java
"by", "for", "with", "and", "the", "from", "about", "quotes", "quote", "please", "show", "find"
```

**After (21 new 3-letter filler words added):**
```java
"by", "for", "with", "and", "the", "from", "about", "quotes", "quote", "please", "show", "find",
"are", "but", "can", "had", "has", "its", "may", "not", "out", "was",
"all", "any", "she", "who", "why", "yet", "you", "how", "let", "too", "now"
```

**Impact:** These common 3-letter words are excluded from content-term filtering and FTS
token extraction. Without them, a query like "how to pray" would include "how" and "to" as
concept terms, leading to false-positive content matches.

---

## Change 5: NEAR Score Boost Changed from Additive to Proportional

**Before:**
```java
// NEAR hits got: score = bm25Score - 50000
// This made ALL NEAR hits sort above ALL AND hits, regardless of BM25 strength
```

**After:**
```java
// NEAR hits get: score = bm25Score * 1000
// Strong BM25 match: -8 → -8000 (ranks highest among NEAR)
// Weak BM25 match:   -2 → -2000 (ranks lower among NEAR)
```

**Impact:** The old additive boost meant a weak NEAR match always outranked a strong AND
match. The multiplier preserves the proximity signal while respecting underlying relevance.

---

## Database Schema (Shared — should be identical on Windows)

Three tables:
- `passages` — `passage_id`, `doc_id`, `text_content`, `locator`
- `documents` — `doc_id`, `author`, `title`, `canonical_url`
- `passages_fts` — FTS5 virtual table backed by `passages`, indexed on `text_content`

FTS5 library on Android: `com.github.requery:sqlite-android:3.49.0` (system SQLite on
Android emulators lacks FTS5).

---

## Search Pipeline Summary (for reference during port)

```
Input → normalizeForMatch() (NFD, lowercase, strip non-alnum)
     → extractFtsTokens() (noise removal, min 3 chars, author stripping, wildcard append)
     → toFtsQueryNear() (2-3 tokens → NEAR(...))
     → buildAndQuery() (AND with optional trailing OR group for 4+ tokens)
     → toFtsQueryOr() (OR fallback)
     → FTS5 query (bm25() ranking, author/title WHERE clauses)
     → filterByRequestedAuthor()
     → filterByRequestedBook()
     → filterByContentTerms() (3+ char non-noise tokens must appear in passage)
     → Phrase LIKE query (LIKE %token1%token2%..., score -99999, skipped if NEAR fired)
     → Book-scoped fallback (if book-scoped and too few results, score -99998)
     → removeBoilerplateAndDuplicates() (< 90 chars, > 15000 chars, nav text, etc.)
     → rankForDisplay() (phrase > NEAR×1000 > BM25)
```

---

## Version Info

- **Android versionName:** 1.3.0
- **Android versionCode:** 5
- **Git commit:** `90d3f2e`
- **Search doc:** `SEARCH.md` (full pipeline documentation)
- **Improvement log:** `Search_Improvements.md`