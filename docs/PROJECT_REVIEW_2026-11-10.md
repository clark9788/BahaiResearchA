# Bahá'í Research — Project and Terms Review

**Date:** 2026-11-10  
**Scope:** `BahaiResearch` (desktop), `BahaiSearchCommon` (shared search), `BahaiResearchA`
(Android); the Department of the Secretariat letter of 6 November 2026; the
`bahai.org/legal` Terms of Use.  
**Purpose:** Pre-launch review before publishing the Android app on Google Play.

> This is an engineering and compliance reading of the documents and code, not legal
> advice. The Bahá'í Reference Library Terms of Use and any written guidance from the
> Bahá'í World Centre or the National Spiritual Assembly govern.

---

## 1. Executive summary

The foundation is strong. The corpus is complete and internally consistent, the paragraph
locators work, the search core is well tested, and the Android app is a credible Play Store
product: offline, no account, no tracking, no API key. The Windows app is a useful reference
tool but is not the product.

The app should **not** be submitted yet. The remaining work is small but important:

1. The Android author dropdown is hardcoded in ASCII and cannot find three of the six
   authors. This was fixed immediately after this review (see §3.1).
2. There is no in-app About/Sources & Attribution screen. This is both a UX gap and a
   compliance requirement under the Library terms.
3. Release signing is not configured (`keystore.properties` is absent), so a terminal
   `bundleRelease` produces an unsigned AAB.
4. The Play Store metadata and assets are not complete: privacy-policy URL, Data Safety,
   content rating, screenshots, feature graphic, and updated listing text.
5. The letter from the Department of the Secretariat is a general statement subject to the
   Terms, not a specific approval of the app or a licence for offline full-corpus
   distribution or the four derived XHTML editions. Specific written confirmation for those
   points is still prudent.

**Verdict:** a free, attributed, non-commercial app that preserves the source and clearly
disclaims affiliation is consistent with the plain terms. Close the notice/disclaimer and
scope-confirmation gaps before production.

---

## 2. Current state

| Project | Version | Tests | Role |
|---|---|---|---|
| `BahaiSearchCommon` | 1.1.1 | 98 | Shared pure search logic |
| `BahaiResearch` | 1.6.0 | 12 | Desktop reference / community tool |
| `BahaiResearchA` | 1.10.0 (`versionCode` 14) | 2 | Android product |

Corpus: **95 works / 33,980 passages**, all XHTML, zero `p.N` locators. The four former
PDF/DOCX works are now derived XHTML editions with paragraph locators; all four resolve.

Android build facts:

- `compileSdk`/`targetSdk` 36, `minSdk` 24.
- Android Lint: **0 errors, 29 warnings** (hardcoded text, dependency versions, RTL padding,
  autofill, `notifyDataSetChanged`).
- No `INTERNET` permission. The app is fully offline; no analytics, ads, or AI.
- Debug APK: ~42 MB; bundled `corpus.db`: 36 MB; XHTML assets: ~27 MB on disk.
- `allowBackup="true"` with empty backup-rule samples; the 36 MB database is therefore a
  backup candidate unless excluded.
- `keystore.properties` is absent, so release signing is not configured in the working tree.

---

## 3. Project review

### 3.1 P0 — fix before publishing

#### 1. Android author filter (fixed in this pass)

`MainActivity.initAuthorSpinner()` hardcoded ASCII values:

```java
List.of(ALL_AUTHORS, "Baha'u'llah", "Bab", "'Abdu'l-Baha", ...)
```

The DB stores accented values. SQLite `lower()` is ASCII-only, so the SQL filter
`lower(d.author) = lower(?)` and the title query returned nothing for three authors:

```text
Baha'u'llah  -> 0 documents    Bahá'u'lláh  -> 14
Bab          -> 0 documents    Báb          -> 1
'Abdu'l-Baha -> 0 documents    'Abdu'l-Bahá -> 15
```

**Resolution applied 2026-11-10:** the author spinner is now populated from
`SELECT DISTINCT author FROM documents` after the database opens, using the exact stored
(accented) values. The six authors keep the intended order — Bahá'u'lláh, Báb,
'Abdu'l-Bahá, Shoghi Effendi, Universal House of Justice, Compilation — and any future
author is appended alphabetically. Titles are sorted with
`SearchCore.normalizeForMatch()`, which strips accents and punctuation, so
`Ḥuqúqu'lláh` sorts under H and `'Abdu'l-Bahá` sorts under A. The `List.of` and `isBlank`
calls in this path were removed as part of the fix. The Android debug build and unit tests
pass; device confirmation remains.

**Follow-up test:** select each of the six authors on a device; confirm the title list is
populated and a search returns results for `Bahá'u'lláh`, `Báb`, and `'Abdu'l-Bahá`.

#### 2. About / Sources & Attribution screen

The app has no menu or About activity. The only legal text is in the XHTML source footers.
`BuildRelease.md` requires the notice in the app/legal screen (§9.3) and the release
checklist marks it as an open item.

The screen should include:

- app name and version;
- the exact `Copyright © Bahá'í International Community` notice and the Library/terms links;
- the “independent, free, non-commercial; not affiliated, sponsored, or endorsed” disclaimer;
- author credits and the note that the four derived editions are transcriptions, not official
  editions;
- privacy statement (offline, no data collection), contact email, and corpus version/date.

Make it reachable from the main screen, not buried in a long-press menu.

#### 3. Release signing

`app/build.gradle` reads `keystore.properties` or environment variables, but the file is not
present. A terminal `bundleRelease` therefore produces an unsigned AAB. Run
`tools/configure-signing.sh`, verify the keystore alias (the document uses `upload`) and
passwords, then build and verify:

```bash
./tools/configure-signing.sh
./gradlew :app:bundleRelease
jarsigner -verify -verbose app/build/outputs/bundle/release/app-release.aab
```

#### 4. Play Store metadata and assets

- Privacy-policy URL (the template in `BuildRelease.md` §9.1).
- Data Safety answers (no collection/sharing; no ads; no account).
- Content rating questionnaire.
- Screenshots and a 1024×500 feature graphic; `ic_launcher-playstore.png` exists.
- Updated short/full description that does not imply official affiliation.
- Support email and app-access declaration (no login).

The current `README.md` says the app is not on the Play Store and cites 22,000 passages;
both are stale.

#### 5. Terms and scope confirmation

The 6 November 2026 letter is a general statement that Library material may be used subject
to the Terms. It is not a specific approval or endorsement. The two areas it does not
specifically address are offline full-corpus distribution and the four derived XHTML
editions. Seek a short written confirmation from `termsofuse@bahai.org` and, as the letter
suggests, the National Spiritual Assembly. Keep the letter and reply with the release
records.

#### 6. Backup

The 36 MB database is a poor Auto Backup candidate and may exceed the 25 MB limit. Set
`allowBackup="false"` or exclude the `corpus.db` database in both `backup_rules.xml` and
`data_extraction_rules.xml`. Align the privacy policy.

#### 7. Device/API test matrix

`minSdk` is 24. Lint reports no `NewApi` errors, and D8 backports the current `List.of` /
`isBlank` calls, but test on API 24, 29, 33 and a low-end device. Measure the phrase `LIKE`
query on older hardware; it scans the passages table.

### 3.2 P1 — high value before launch

- **Visible Source action.** Source is only reachable by long-pressing a result. Add a
  visible “Source” button/icon; context is the product’s differentiator.
- **Search help.** The only hint is “1-4 key words for most exact.” Add a short Help/About
  section covering keywords, NEAR/AND/OR, author/title filters, and why filler results
  appear.
- **Clearer summary.** Android says “Found N — searched: …”; the desktop says “exact search
  found nothing; broadened to: …”. Use the clearer “broadened” wording and explain that
  related passages are included to suggest better terms.
- **Copy with citation.** Include the source/edition/copyright line, not just author/title.
- **Desktop prompt and AI default.** The desktop still says “Describe your topic in paragraph
  form” while the hint says keywords. Change the prompt to keyword-first and default the
  shipped profile to `research.aiMode=none`; the AI reranker added 28 seconds in a live log.
- **Clean shipped DB paths.** `documents.source_snapshot_path` contains
  `/home/clarkdev/...` absolute paths. Replace with relative paths or null in the Android
  asset DB.
- **Docs cleanup.** Update the Android `README.md`, `plan.md`, `ANDROID-PLAN.md`,
  `SEARCH.md`, `Search_flow.md`, and the `BahaiSearchCommon` README (Windows paths,
  1.3.0-SNAPSHOT, “behave identically”). Archive the exploratory Flutter/iOS/Windows-port
  documents or move them to `docs/archive/`.
- **Accessibility/localization.** Move UI/legal text to `strings.xml`; add a content
  description to the source viewer’s “✕”; consider `DiffUtil`.
- **Tests.** Android has two unit tests; add an instrumentation test for the DB copy,
  accented author selection, and a known query. Add a desktop integration test for the
  fallback chain.

### 3.3 P2 — after launch

- **Consolidate the search pipeline.** `SearchCore` is shared, but the desktop and Android
  `LocalCorpusSearchService` classes duplicate SQL and flow, and both had to be patched for
  the ranking/OR fixes. Move the pipeline into `SearchCommon` behind a small database
  interface, or add a parity test.
- **Make Android the reference implementation.** It is the product and is simpler (no AI).
- **CI.** Run tests/lint for all three repositories on push; the composite builds need both
  repositories checked out.
- **Corpus version in About.** Show the app and corpus build/version.
- **Localization.** The corpus is English, but the UI strings could be localized later.
- **Windows.** Keep it community-supported; a zip with the runtime and `run-app` instructions
  is more reliable than fighting WiX/Smart App Control.

---

## 4. Terms and letter review

### 4.1 What the 6 November 2026 letter says

The Department of the Secretariat letter (responding to the 14 May 2026 email) says:

- In general, material from bahai.org and associated sites may be used **subject to the
  Terms of Use** at `bahai.org/legal`.
- You are welcome to draw on the Reference Library material **in accordance with those
  terms**.
- The app must make clear that it is a **personal initiative and not associated with the
  institutions of the Faith**.
- Questions may be directed to the National Spiritual Assembly.

It does **not** say:

- “Your app is approved.”
- That offline bundling of the complete corpus is permitted.
- That converting PDF/DOCX sources into derived XHTML editions is permitted.
- That the commercial-use permission is waived.
- That the Bahá'í International Community endorses the app.

Treat it as **guidance and evidence of good faith**, not a licence or an endorsement. Do not
describe the app in the Play listing as “approved by the Bahá'í World Centre.”

### 4.2 Terms-to-app checklist

| Term | Requirement | Current app |
|---|---|---|
| Copyright | Content is protected | App bundles the corpus |
| Notice | “Copyright © Bahá'í International Community” + attribution to the source + individual author where named | Not yet in the app UI or listing; source footers carry per-work source-edition copyrights and Library links |
| Intent | Must not misrepresent the original source | Derived-edition footers say “derived transcription, not an official edition”; About screen still needed |
| Editing | A/V content may only be cropped/edited for size; text must not misrepresent intent | Four editions are structural transcriptions; defensible, but confirm scope |
| Commercial | Prior permission required | App is free, no ads/IAP — keep it that way |
| Withdrawal | BIC may withdraw permission at any time; use must cease immediately | Be prepared to unpublish/update |
| Personal initiative | Must be clear it is not associated with the institutions | In `BuildRelease.md`, not yet in the app/listing |
| Questions | May turn to the National Spiritual Assembly | Recommended for the gray areas below |

### 4.3 Gray areas needing specific confirmation

1. **Offline full-corpus distribution.** The Terms permit use subject to conditions; they do
   not explicitly address shipping the complete Library corpus inside an app. The free,
   non-commercial nature helps, but confirm.
2. **The four derived XHTML editions.** Converting PDF/DOCX sources, repairing hyphenation,
   restoring accents, and adding structure is a transformation. The utility’s ground rule —
   mechanical transformations only, no meaning changes — is the right position, but confirm
   that the derived editions are acceptable. The source editions also carry their own
   copyrights (Palabra 2017; NSA 1996/2009/2024), which the footers preserve.

Ask `termsofuse@bahai.org` and the National Spiritual Assembly for a short written
confirmation covering:

- offline distribution of the complete corpus in a free app;
- the four derived XHTML editions;
- Google Play distribution;
- confirmation that the About-screen notice and attribution satisfy the Terms.

### 4.4 Commercial status and withdrawal

The Play Store registration fee is a developer cost, not a commercial use of the Content. The
app must remain free and non-commercial: no ads, in-app purchases, donations, or paid tiers
without prior permission. If monetization is ever added, seek permission first.

The BIC reserves the right to withdraw permission at any time. Keep the contact email
monitored and know how to unpublish the app or push an update that removes the content.

### 4.5 Exact notice to display

Use the text already in `BuildRelease.md` §9.3:

```text
Copyright © Bahá'í International Community

The Bahá'í writings and related source content included in this application are provided
from the Bahá'í Reference Library at https://www.bahai.org/library/ and are used subject to
the terms of use at https://www.bahai.org/legal.

This application is an independent, free, non-commercial study and research tool. It is not
affiliated with, sponsored by, or endorsed by the Bahá'í International Community. The
application does not alter the intent, character, nature, or meaning of the source content.
Individual authors are credited where named in the source.
```

Put it in the About/Legal screen, the Play listing/legal page, and the
“Copy with citation” output. The four derived-edition footers should also carry it; the 91
Library-downloaded files can remain as downloaded, with the app-level notice covering the
collection.

---

## 5. Pre-launch checklist

- [x] Android author spinner loads accented authors from the DB; all six authors return
      titles and results. *(Code fixed 2026-11-10; device confirmation pending.)*
- [ ] About/Sources & Attribution screen with the exact notice and disclaimer.
- [ ] Notice/disclaimer in the Play listing and privacy/legal page.
- [ ] “Copy with citation” includes source/attribution/notice.
- [ ] Signed AAB produced and verified; upload key matches the previous release.
- [ ] Privacy policy hosted and entered in Play Console.
- [ ] Data Safety, content rating, app access, ads declaration completed.
- [ ] Screenshots, feature graphic, icon, short/full description ready.
- [ ] `allowBackup`/backup rules exclude the 36 MB database, or backup is disabled.
- [ ] Shipped DB has no absolute developer paths.
- [ ] Device tests on API 24/29/33 and a low-end phone; phrase search performance checked.
- [ ] Written scope confirmation for offline corpus/derived editions/Play distribution.
- [ ] Letter and reply kept with release records.
- [ ] README/docs updated (counts, Play availability, release notes, license/attribution).
- [ ] Internal testing track and pre-launch report completed before production.

---

## 6. Suggested sequence

1. Finish the author fix and test all six authors on a device. *(Author fix is in this pass;
   device confirmation remains.)*
2. Add the About/Sources & Attribution screen and the copy-citation notice.
3. Configure signing, build a signed AAB, and verify the signature.
4. Complete the Play listing, privacy policy, Data Safety, and content rating.
5. Exclude the DB from backup; clean the shipped DB paths.
6. Run the device/API test pass and internal testing track.
7. Send the specific scope confirmation request to `termsofuse@bahai.org` / the National
   Spiritual Assembly; keep the response.
8. Production rollout, then the post-launch cleanup (shared search pipeline, tests, docs).

---

## 7. Evidence

- Corpus: 95 documents, 33,980 passages, zero `p.N` locators; all four derived editions
  resolve every locator.
- Author mismatch (before the fix): `Baha'u'llah`/`Bab`/`'Abdu'l-Baha` returned 0 documents;
  accented forms returned 14/1/15.
- Android Lint: 0 errors, 29 warnings; no `NewApi` errors. The author path no longer uses
  `List.of`/`isBlank`; D8 had backported them, but the database-driven lists remove that
  dependency.
- Test counts: `BahaiSearchCommon` 98, `BahaiResearch` 12, `BahaiResearchA` 2.
- Key files: `BuildRelease.md` §9.1–9.4 and §14; `BIC_Attribution_Notice.md`;
  `app/src/main/java/com/bahairesearch/android/MainActivity.java`;
  `app/src/main/AndroidManifest.xml`;
  `app/src/main/res/layout/activity_main.xml`;
  `app/src/main/java/com/bahairesearch/android/ResultsAdapter.java`.

---

*Review prepared 2026-11-10. It is an engineering and compliance reading, not legal advice.
Re-check the current Terms of Use before each release; the terms may be revised.*
