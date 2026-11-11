# Bahá'í Research

An offline Android app for searching authoritative Bahá'í writings. Searches 25,404 passages from 91 works in the sacred texts and returns the most relevant results ranked by relevance.

## Features

- Full-text search across 25,404 passages
- Filter by author and by individual work
- Results ranked by relevance using FTS5
- Proximity search (NEAR) for 2–3 word queries finds tightly co-occurring terms
- Long press any result to copy the passage, copy with citation, or open the source file
- Source viewer opens the full XHTML source scrolled to the exact paragraph
- Two-finger pinch resizes the reading text in the results list and the source viewer, and the chosen size is remembered between launches
- Works completely offline — no internet connection required

## Authors Included

- Bahá'u'lláh
- The Báb
- 'Abdu'l-Bahá
- Shoghi Effendi
- Universal House of Justice
- Compilations

## How to Use

1. Optionally select an author from the first dropdown
2. Optionally select a specific work from the second dropdown (enabled after choosing an author)
3. Enter 2–4 key words for the most exact results
4. Tap **Search**
5. Long press any result to copy the passage, copy with citation, or open the source file
6. Pinch with two fingers on the results or the open source to adjust the reading text size

## Installation

The planned distribution channel is Google Play. A sideload APK is available for testing:

1. Download the latest `app-release.apk` from the [Releases](../../releases) page
2. On your Android device, open the downloaded file
3. If prompted, enable **Install unknown apps** for your browser or file manager
4. Tap **Install**

Requires Android 7.0 (API 24) or higher.

## Updating the Corpus

The corpus database (`corpus.db`) is bundled in the APK and copied to internal storage on first launch. The app records the `versionCode` of the copy and re-copies the bundled database on the first launch after any app update (whether or not the corpus changed), so a revised corpus in a new release is picked up automatically — no uninstall or reinstall required. Later launches on the same version reuse the existing copy without re-copying.

## Building from Source

Requirements: Android Studio, JDK 11

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle
4. Build → Generate Signed Bundle / APK to produce a release APK

## Release Notes

### 1.10.1 (first Google Play release)

- Corpus is now 91 authoritative Library works / 25,404 passages. The four derived XHTML editions (Framework for Action and the three Messages volumes) were removed because their source PDF/DOCX files are not bundled with the app.
- Author dropdown now loads accented author names from the database; title lists sort diacritic-insensitively.
- Book-scoped filler results rank after real matches; failed NEAR/AND searches broaden to OR instead of returning no results.

### 1.9.0

- Added new source Living the Life.

### 1.8.0

- Two-finger pinch changes the reading text size of both the results list and the source viewer, and the chosen size is remembered between launches.

## License

Free, non-commercial personal and community study tool. The corpus is sourced from the Bahá'í Reference Library and used subject to the terms at https://www.bahai.org/legal. This app is independent and not affiliated with, sponsored by, or endorsed by the Bahá'í International Community.
