# Plan: About/Attribution, How-to-use, and configurable result count

**Status:** Phases 1–2 implemented; Phase 3 pending
**Date:** 2026-11-13
**Goal:** Make the app's origin, source licensing, and usage explicit to users, and give
users control over how many passages a search returns.

---

## 1. Requests covered

| # | Request | Phase |
|---|---|---|
| 1 | Attribution page stating this is a personal initiative, not associated with the institutions of the Faith | 1 |
| 2 | Copyright © Bahá'í International Community added to the author/title attribution | 1 |
| 3 | Note that "Compilation" sources expose the original author via long-press → Open source | 1 |
| 4 | How-to-use page: 1–3 word searching, pinch to resize text, long-press actions | 1 |
| 5 | User-selectable `maxQuotes` (passages returned per search) | 2 |
| 6 | Keep docs, Play listing, privacy policy, and release process in sync | 3 |

## 2. What exists today

- Result cards show `— Author · Title` only ([ResultsAdapter.java](../app/src/main/java/com/bahairesearch/android/ResultsAdapter.java)).
- Long-press menu: **Copy passage**, **Copy with citation**, **Open source** (same file).
- **Open source** opens the bundled XHTML from `bahai.org/library` at the paragraph anchor
  ([SourceViewerFragment.java](../app/src/main/java/com/bahairesearch/android/SourceViewerFragment.java)).
- Two-finger pinch changes reading size and persists it ([TextScalePrefs.java](../app/src/main/java/com/bahairesearch/android/config/TextScalePrefs.java)).
- `maxQuotes` is hardcoded to 12 in `AppConfig.defaults()` and used by
  `LocalCorpusSearchService` to cap results.
- There is no About/Help screen, no overflow menu, and no settings screen.

## 3. Phase 1 — Attribution and How-to-use pages (this change)

### Entry point

A `⋮` overflow button in the main title row opens a menu with **How to use** and
**About & sources**. Both open a new `InfoActivity` that renders a bundled HTML page. The
same menu later gains **Settings** for Phase 2.

### About & sources page

Sections, in order:

1. **Personal initiative** — a boxed notice: personal, non-commercial project; not
   affiliated with, sponsored by, or endorsed by the Bahá'í International Community, the
   Universal House of Justice, the National Spiritual Assembly, or any other institution of
   the Faith; nothing in the app is an official statement.
2. **Copyright and terms of use** — the exact notice required by `bahai.org/legal`:
   `Copyright © Bahá'í International Community`, the Bahá'í Reference Library source link,
   and the Terms of Use link, plus the "does not alter intent" sentence.
3. **Author and title attribution** — explains that each card's author/title line credits the
   source document, and that copyright remains with BIC and the respective rights holders.
4. **Compilations** — explains that `Compilation` documents are drawn from the original
   writings, and that long-press → **Open source** opens the original document from
   bahai.org/library, which credits the original author and source where named.
5. **Offline use and privacy** — no accounts, ads, analytics, or data collection.
6. Version line (`BuildConfig.VERSION_NAME` / `VERSION_CODE`).

### How-to-use page

1. **Search with one to three key words** — why 1–3 is best; more than three words makes the
   extras optional and broadens results.
2. **Filter by author and title** — All Authors / All Titles.
3. **Read the results** — relevance ranking, card contents.
4. **Change the text size** — two-finger pinch, remembered, applies to the source viewer.
5. **Long-press a result** — Copy passage / Copy with citation / Open source.
6. **Compilations** — long-press → Open source for the original author/source.
7. **Works offline**.

### List footer copyright

A single muted footer `© Bahá'í International Community` follows the last result card, so the
notice is present in the results view without repeating on every card. It is hidden when the
result list is empty.

### Acceptance criteria

- Both pages open from the main screen and return with the header back button or system back.
- Pages respect light/dark theme and the system font size.
- Links open in the browser, not inside the app.
- The About page contains the exact BIC copyright notice and the personal-initiative
  disclaimer.
- Release build passes `lintVital` and produces a signed APK.
- Copy is reviewed by the maintainer before release.

## 4. Phase 2 — `maxQuotes` setting (implemented)

- `SearchLimits` holds the bounds (default 12, range 3–50) with no Android dependencies.
- `SearchPrefs` (SharedPreferences) stores the chosen value and clamps on read and write.
- `AppConfig.forSession(maxQuotes)` builds the session config; `ResearchService.search(...)`
  takes the limit from the caller, which `MainActivity` reads from `SearchPrefs` before each
  search.
- UI: **Settings** in the overflow menu opens a Material dialog with a 3–50 slider, a live
  value label, and Save/Cancel. Saving shows a confirmation toast and applies from the next
  search.
- The status summary already reports the returned count, so it reflects the limit.
- Tests: `SearchLimitsTest` covers clamping below, above, inside, and the default.

## 5. Phase 3 — Release follow-through

- Update Play listing description and the privacy-policy template with the same wording.
- Add the About-page requirement to `BuildRelease.md` §14 checklist.
- Re-verify Data Safety answers (still: no data collected).
- Bump `versionCode`/`versionName`, build the AAB, verify the upload-key signature, tag.
- Update `docs/PROJECT_REVIEW_2026-11-10.md` gap list.

## 6. Files changed in Phase 1

| File | Change |
|---|---|
| `app/src/main/assets/info/about.html` | New: About & sources content |
| `app/src/main/assets/info/help.html` | New: How-to-use content |
| `app/src/main/java/.../InfoActivity.java` | New: renders an info page, theme-aware |
| `app/src/main/res/layout/activity_info.xml` | New: header + WebView |
| `app/src/main/res/layout/activity_main.xml` | Title row gains the `⋮` button |
| `app/src/main/java/.../MainActivity.java` | Overflow menu wiring |
| `app/src/main/res/layout/result_item.xml` | Copyright line on each card |
| `app/src/main/java/.../ResultsAdapter.java` | Binds/scales the copyright line |
| `res/values/strings.xml`, `colors.xml`, `values-night/colors.xml`, `dimens.xml` | Titles, colors, size |
| `app/src/main/AndroidManifest.xml` | Register `InfoActivity` |
| `app/src/main/java/.../config/SearchLimits.java` | Phase 2: bounds and clamping |
| `app/src/main/java/.../config/SearchPrefs.java` | Phase 2: persisted result limit |
| `app/src/main/res/layout/dialog_max_quotes.xml` | Phase 2: settings dialog |
| `app/src/test/java/.../config/SearchLimitsTest.java` | Phase 2: clamp tests |
