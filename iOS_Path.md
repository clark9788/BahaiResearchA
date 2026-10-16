# iOS Path

How to get an iPhone build. Written after reviewing [Convert_to_Flutter.md](Convert_to_Flutter.md);
the recommendation there — rewrite the portfolio in Flutter — does not fit this codebase.

## Decision

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
not exist here. A PWA is the only zero-Mac option, but a 35 MB offline corpus plus iOS
Safari storage limits make it a poor primary target.
