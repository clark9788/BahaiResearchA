# BahaiResearch Android — Status

## Shipped Versions

| Version | versionCode | What shipped |
|---|---|---|
| 1.0 | 1 | Core search, author/title spinners, FTS5 via bundled SQLite (requery 3.49.0), copy/copy-with-citation |
| 1.1 | 2 | NEAR proximity search for 2-token queries; phrase LIKE skipped when NEAR fires; UHJ source priority removed; Javadoc added throughout |
| 1.2 | 3 | Source viewer: long-press any result → "Open source" opens the full XHTML source file in a full-screen WebView scrolled to the exact paragraph anchor |

---

## Architecture

- **Search engine:** `LocalCorpusSearchService` — FTS5 NEAR → AND → OR tier ladder, phrase LIKE merge, boilerplate removal, quality-band ranking
- **Database:** `corpus.db` bundled in assets, copied to internal storage on first launch via `DatabaseHelper`
- **Source files:** 90 XHTML files bundled under `app/src/main/assets/curated/en/html/`, paths match `canonical_url` in corpus.db exactly
- **Source viewer:** `SourceViewerFragment` — full-screen DialogFragment, WebView loads `file:///android_asset/curated/en/html/<file>.xhtml#<locator>`
- **Non-XHTML sources:** 4 UHJ texts (2 docx, 2 pdf) — "Open source" menu item not shown for these

---

## Known Limitations / Future Work

### 1. Database version check (important before any corpus update)
`DatabaseHelper` copies `corpus.db` only if the file does not exist in internal storage.
It does **not** detect a newer version of the database when the app is updated.
If the corpus is ever revised, users must uninstall and reinstall to get the updated database.
A version-check mechanism (e.g. compare a version integer stored in the DB against a value in `BuildConfig`) must be added before any corpus update is released.

### 2. Non-XHTML source files
The four UHJ texts (muhj-1963-1986.docx, muhj-1986-2001.pdf, muhj-2001-2022.docx, framework-action.pdf)
have no XHTML equivalent available. "Open source" is silently omitted for results from these works.
Waiting on HTML versions from the Bahá'í World Centre.

### 3. `minPassageLength` not enforced
`AppConfig.minPassageLength` (80) is defined but never applied during filtering.
Very short fragments can still surface in results.

### 4. No search history
Frequently-used search terms must be retyped. A lightweight history (last 10 queries in SharedPreferences) would improve UX.
