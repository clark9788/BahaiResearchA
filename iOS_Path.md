# iOS Path

How to get an iPhone build. Written after reviewing [Convert_to_Flutter.md](Convert_to_Flutter.md);
the recommendation there — rewrite the portfolio in Flutter — does not fit this codebase.

## Status: deferred (2026-10-16)

**Decision: no iPhone build for now. Stay with Java on Android and the desktop.**

The app is a free charity project with no revenue. A native iOS app means a recurring
US$99/year Apple Developer Program fee, a Mac for building and shipping, and an ongoing
App Store review and OS-update cycle — for a platform where the writings are already
reachable through the Bahá'í Reference Library in Safari.

Java is also the deliberate choice for the existing apps: years of familiarity, plus the
JVM ecosystem the project actually depends on (PDFBox, sqlite-jdbc, JUnit, the
jlink/Inno Setup packaging). Nothing in the mobile or desktop app requires a rewrite.

The analysis below is kept as the plan if that trade-off ever changes.

## Recommendation if revisited

**Build a native Swift iOS reader. Leave the desktop and Android apps in Java.**

The mobile app is a reader. The desktop app is a corpus factory and AI lab. They are
different products that share a database file, not a codebase.

## Why not Flutter

| | BahaiResearch (desktop) | BahaiResearchA (Android) |
|---|---|---|
| Size | 3,121 LOC | 1,281 LOC |
| Corpus role | **builds** `corpus.db` (XHTML/PDF/DOCX ingest) | reads it, read-only |
| AI | Gemini intent + rerank (`GeminiClient`, 270 LOC) | none (documented as intentional) |
| Source display | local `HttpServer` + browser | `WebView` fragment |
| Packaging | jlink runtime image + Inno Setup | APK |

The desktop side produces the artifact every other platform consumes. Rewriting it in
Flutter means discarding PDFBox, sqlite-jdbc, the runtime image, and the installer in
exchange for nothing — Flutter's "one codebase" premise only pays off when all platforms
share a UI, and these two deliberately do not.

## The real shared artifact

`corpus.db` is language-neutral and already produced by the desktop ingest. That is the
cross-platform contract — not `BahaiSearchCommon`, which shares only ~455 LOC
(`SearchCore` 382 + models 73). The pipelines that actually decide ranking
(`LocalCorpusSearchService`) are **924 LOC on desktop and 357 LOC on Android**, duplicated
and already diverged — which is why the `!nearFired` fix had to be applied twice.

`BahaiSearchCommon` is also a JVM-only `java-library`, so it cannot serve iOS at all. The
search logic must be re-implemented once for iOS whatever framework is chosen. That is a
bounded job, not a reason to rewrite Android.

## Plan

1. Bundle the same `corpus.db`.
2. On first launch, copy it to the app sandbox and open it read-only — mirroring
   [`DatabaseHelper.copyIfNeeded` / `open`](app/src/main/java/com/bahairesearch/android/corpus/DatabaseHelper.java)
   in the Android app.
3. Query it with an FTS5-capable SQLite (see gotcha below).
4. Port `SearchCore` (382 LOC) to Swift, and `SearchCoreTest` (713 LOC) to XCTest.
5. Render source XHTML in `WKWebView`, reusing the Android anchor-scroll approach.

## The one real gotcha: FTS5 on iOS

Apple's system SQLite ships FTS3/FTS4 but **not reliably FTS5**, and this corpus is built
with FTS5. Bundle a custom SQLite — GRDB with a custom build, or the GRDB/SQLCipher
subspec. See the GRDB notes on
[enabling FTS5](https://github.com/groue/GRDB.swift/blob/master/Documentation/FullTextSearch.md).

## Open decision: Swift vs Kotlin Multiplatform

Decide by churn rate of `SearchCore`, not by preference:

- **Near-stable → native Swift.** Fewest moving parts, best platform fit.
- **Still changing often → Kotlin Multiplatform.** One implementation instead of three,
  at the cost of a new toolchain and moving Android onto the Kotlin artifact.

Either way, the desktop stays Java.

## Contract tests

Three implementations will drift. Make `SearchCoreTest` plus a small language-neutral
golden-query file (query → expected top passage IDs) mandatory on every platform. That
catches divergence better than sharing Java source did.

## Web/PWA option — feasibility and caveats

The only genuinely zero-fee route to iPhone: a Progressive Web App — a website plus a
manifest and service worker that the user adds to the home screen. No Apple fee, no Mac,
buildable and hosted from Ubuntu.

How the corpus would run:

- Serve `corpus.db` once (~35 MB, much smaller gzipped) and query it with SQLite compiled
  to WebAssembly, so the same FTS5 tables and ranking apply as on desktop and Android.
- Cache the database for offline reuse (IndexedDB, or OPFS where available).
- A small web UI: search box, filters, results list, and an iframe for the XHTML source.

Caveats — all real, none fatal:

- **FTS5 must be compiled into the WASM build.** The stock `sql.js` build has historically
  shipped without it and silently degrades to a LIKE scan; packages such as
  [fts5sqljs](https://www.npmjs.com/package/fts5sqljs) exist precisely for that reason.
  Verify ranking parity before committing.
- **iOS storage eviction.** Safari caps script-writable storage at roughly 7 days of use.
  Home-screen web apps are carved out of that ([WebKit bug 209501](https://bugs.webkit.org/show_bug.cgi?id=209501)),
  so offline data persists reliably only after the user installs it; the persistent-storage
  API itself is still an open WebKit item ([bug 209563](https://bugs.webkit.org/show_bug.cgi?id=209563)).
- **OPFS on iOS Safari is uneven** (`createSyncAccessHandle()` failures are reported), so
  plan on loading the database into memory — fine at 35 MB, but memory pressure can reload
  the tab.
- **A 35 MB first load** over mobile data; ship a trimmed corpus or fetch on Wi-Fi.
- **It is a third implementation** — JS/TS plus WASM tooling, a second UI, and another copy
  of the search plumbing to keep in sync with Android and desktop.
- **Hosting** is free but not unlimited: [GitHub Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/github-pages-limits)
  is ~1 GB site size with a soft ~100 GB/month bandwidth.

Verdict: viable and truly zero-cost, but it introduces the new language/ecosystem and the
third codebase that the Java decision was meant to avoid. Ranked for this project's stated
priorities: (1) skip iPhone, (2) PWA, (3) native Swift.

## Other platforms (HarmonyOS)

Same shape of problem, weaker case. HarmonyOS NEXT (HarmonyOS 5) dropped Android
compatibility, so the existing APK does not run on it; a build there means another native
stack (ArkTS/ArkUI, DevEco Studio on Windows/macOS) and distribution through Huawei
AppGallery, aimed at a China-centric audience. For a free Bahá'í reading app the reach does
not justify another codebase. Not pursued.

## Reality check

- A Mac is required to build, sign, and ship to the App Store (used M1 mini, or a cloud
  Mac for builds). Apple Developer Program membership; TestFlight for testing.
- Everything else can stay on Ubuntu.
- Fix `BahaiSearchCommon`'s build coupling regardless: both consumers declare a Maven
  coordinate but resolve it from a sibling checkout via `includeBuild`, so standalone
  clones and CI break without it.

## Not recommended

Flutter and Compose Multiplatform both require a full Java→Dart/Kotlin rewrite of a
working Android app, and abandon `BahaiSearchCommon`, to solve a desktop problem that does
not exist here. A PWA is the only zero-fee option — workable, but it adds a third
implementation; see the feasibility section above.
