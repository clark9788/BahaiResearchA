# BahaiResearch Android — Build Plan

## Current State

Android Studio project created, Gradle wrapper in place. DB is ready at
`app/src/main/assets/corpus.db` (35.7 MB). Six Java files copied from the
Windows project but **none compile yet**: all have wrong package names
(`com.bahairesearch.*` instead of `com.bahairesearch.android.*`), use JDBC
(`java.sql.*`) instead of Android SQLite, and reference GeminiClient and
helper classes that don't exist in this project. No Activity, no layout,
no UI exists at all.

---

## The Four Missing Windows Classes — Verdict

| Class | Port it? | Reason |
|---|---|---|
| `CorpusSearchHit` | Create fresh | 14-line record, trivial to write new |
| `CorpusPaths` | **No** | Resolves `java.nio.file.Path` from AppConfig; Android has one fixed DB path via `context.getDatabasePath()` — concept doesn't apply |
| `CorpusConnectionFactory` | **No** | Opens JDBC connections; Android uses `SQLiteDatabase`, not JDBC — fully replaced by `DatabaseHelper` |
| `CorpusBootstrapService` | **No** | Creates dirs, initializes schema, writes bootstrap JSON; Android ships a pre-built DB — replaced by the assets copy-on-first-run in `DatabaseHelper` |

---

## Source Links

### The canonical_url format
All 89 DB entries are relative paths, e.g.:
```
curated/en/html/advent-divine-justice.xhtml
curated/en/docx/muhj-1963-1986.docx
curated/en/pdf/muhj-1986-2001.pdf
```
The locator column is a numeric paragraph anchor like `990539395`.

The Windows app constructs `file://` local paths pointing to bundled xhtml files.

### The four non-HTML entries
- `curated/en/docx/muhj-1963-1986.docx` — UHJ Messages 1963–1986
- `curated/en/docx/muhj-2001-2022.docx` — UHJ Messages 2001–2022
- `curated/en/pdf/muhj-1986-2001.pdf` — UHJ Messages 1986–2001
- `curated/en/pdf/framework-action.pdf` — Framework for Action

These are all UHJ texts that don't exist as standalone xhtml pages on bahai.org
in the same way the other books do. No reliable deep-link is possible for them.

### Source link decision — see notes below

---

## Phase 1 — Backend (search engine, no UI)

**Goal:** get a raw search query returning results from the DB.

1. **Fix `AppConfig.java`** — strip from 24 fields to 4:
   `noResultsText`, `debugIntent`, `maxQuotes`, `minPassageLength`.
   Fix package to `com.bahairesearch.android.config`.

2. **Replace `ConfigLoader.java`** — delete properties-file loading entirely.
   Add `AppConfig.defaults()` static factory (hardcoded Android values).

3. **Fix model packages** — `QuoteResult.java` and `ResearchReport.java`
   are correct code; change package declaration only.

4. **Create `CorpusSearchHit.java`** — 14-line record:
   `quote, author, title, locator, sourceUrl, score`.

5. **Create `DatabaseHelper.java`** — the most critical new piece:
   - Subclass of `SQLiteOpenHelper` (or plain helper class)
   - `initDatabase(Context)`: if DB doesn't exist in internal storage, copy  
     from `assets/corpus.db` to `context.getDatabasePath("corpus.db")`   && So this increases the app size by 35MB, just stored somewhere else. Should the app delete corpus.db
     (streaming copy, ~35 MB, must run on background thread)
   - Returns `SQLiteDatabase` opened read-only after copy

6. **Rewrite `LocalCorpusSearchService.java`** — bulk of the work:
   - Fix package
   - Strip ALL AI: remove `resolveLocalQueryIntent`, `rerankLocalCandidates`,
     semantic fallback block, intent logging, AI concept merging, `GeminiClient`
     import
   - Replace JDBC (`Connection → PreparedStatement → ResultSet`) with
     Android SQLite (`SQLiteDatabase → Cursor`)
   - Keep all pure logic unchanged: `toFtsQuery`, `toFtsQueryOr`,
     `extractFtsTokens`, `buildAndQuery`, `inferRequiredAuthor`,
     `filterByRequestedAuthor`, `filterByContentTerms`, `filterByRequestedBook`,
     `rankForDisplay`, `removeBoilerplateAndDuplicates`, phrase search,
     book token helpers, normalization
   - `search()` method becomes ~40 lines (no AI path at all)
   - Takes `SQLiteDatabase db` as parameter instead of `AppConfig`

7. **Simplify `ResearchService.java`** — thin wrapper:
   fix package, remove all bootstrap/ingest/Gemini calls,
   just validate input → call `LocalCorpusSearchService.search()` → return.

8. **Delete `CorpusIngestService.java`** — not needed on Android.

9. **Bump Java version** — `build.gradle`: change
   `sourceCompatibility` and `targetCompatibility` to `VERSION_17`
   (records require Java 16+).

---

## Phase 2 — UI

10. **`activity_main.xml`** — vertical LinearLayout or ConstraintLayout:
    - Author `Spinner` ("All Authors" + 5 canonical values)
    - Title `Spinner` (repopulates when author changes)
    - `EditText` for query
    - Search `Button`
    - Status `TextView` (result count / loading / error)
    - `RecyclerView` for results

11. **`result_item.xml`** — card per result (see source-link discussion).

12. **`ResultsAdapter.java`** — RecyclerView adapter binding `QuoteResult`
    to `result_item.xml`.

13. **`MainActivity.java`**:
    - `onCreate`: kick off DB copy on background thread (AsyncTask or
      ExecutorService), show ProgressDialog or spinner, disable UI until ready
    - DB ready: populate Author spinner from `SELECT DISTINCT author FROM
      documents ORDER BY author`
    - Author changes: repopulate Title spinner from `SELECT DISTINCT title
      FROM documents WHERE author = ? ORDER BY title` (skip when "All Authors")
    - Search button: validate non-empty query, run search on background thread,
      update RecyclerView on main thread
    - Handle "All Authors" / "All Titles" as null filters

14. **Update `AndroidManifest.xml`** — declare `MainActivity`, add
    `<uses-permission android:name="android.permission.INTERNET" />` if linking.

---

## Phase 3 — Source Links (open question)

 The canonical_url format — all 89 documents are relative paths like curated/en/html/advent-divine-justice.xhtml. The
  Windows app turns these into local file:// URLs pointing to bundled xhtml files. That approach doesn't translate to
  Android without bundling the 19MB xhtml folder.
 
  The 4 non-HTML entries — all UHJ texts (2 docx, 2 pdf). No meaningful deep-link is possible for these regardless of
  approach. They'd silently have no link.

  The real question is UX, not URLs. A phone screen result card is tight. My thinking:

  Option A — Attribution only, no link
  Show — Baha'u'llah · Hidden Words · ¶42. Clean, always consistent. Long-press card to copy text + citation. This is
  actually the most useful action for a scholarly user (copy to paste into notes/writing). Nothing to tap, no dead links
   for the 4 non-HTML entries.

  Option B — Subtle icon for xhtml entries only
  Show attribution line, plus a small external-link icon on the right for the 85 xhtml documents. Tapping it opens
  https://www.bahai.org/library/... — but the numeric anchor ID (like #990539395) likely won't work on the live
  bahai.org pages, so you'd land on the book's page but not the paragraph. Partial benefit.

  Option C — Share sheet
  Tapping a share icon gives: "Copy passage", "Copy with citation", "Open bahai.org book page" (no anchor). Keeps the
  card clean and gives power users an out.

  My recommendation: Option A now, Option C later. Attribution text is always useful; the link is unreliable (anchor IDs
   don't map to bahai.org) and inconsistent (4 docs can't link at all). If you want to add a share icon in a later
  version, the hook is there. Don't block Phase 2 on it.

---

## Phase 4 — Ship

- Test on emulator: DB copy-on-first-run, FTS5 queries, author/title filters
- Confirm FTS5 is available on emulator (API 24+, should be fine)
- Configure signing via Android Studio (generate keystore)
- Build signed release APK (`./gradlew assembleRelease`)
- Create GitHub repo, tag v1.0, upload APK to GitHub Releases
- README: install instructions (enable unknown sources), basic usage

---

## Open Questions / Decisions

- **Source link UX** — see notes; decision needed before Phase 2
- **Title spinner scope** — show all titles when "All Authors" selected, or
  hide/disable title spinner until author is chosen? (Recommend: disable,
  since there are many titles and cross-author filtering is rarely useful)
- **Result count** — how many results to show? Windows default is 8.
  Phone scroll is easier than desktop, so 8–12 seems right.

   