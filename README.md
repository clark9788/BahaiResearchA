# Bahá'í Research

An offline Android app for searching authoritative Bahá'í writings. Searches over 22,000 passages from the sacred texts and returns the most relevant results ranked by relevance.

## Features

- Full-text search across ~22,000 passages
- Filter by author and by individual work
- Results ranked by relevance using FTS5
- Proximity search (NEAR) for 2-word queries finds tightly co-occurring terms
- Long press any result to copy the passage, copy with citation, or open the source file
- Source viewer opens the full XHTML source scrolled to the exact paragraph
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

## Installation

This app is distributed as an APK and is not available on the Google Play Store.

1. Download the latest `app-release.apk` from the [Releases](../../releases) page
2. On your Android device, open the downloaded file
3. If prompted, enable **Install unknown apps** for your browser or file manager
4. Tap **Install**

Requires Android 7.0 (API 24) or higher.

## Updating the Corpus

The corpus database (`corpus.db`) is bundled in the APK and copied to internal storage on first launch. The app checks only whether the file exists — it does **not** detect a newer version of the database on app update. If the corpus is ever revised, users must uninstall and reinstall the app to receive the updated database. A version-check mechanism should be added before any corpus update is released.

## Building from Source

Requirements: Android Studio, JDK 11

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle
4. Build → Generate Signed Bundle / APK to produce a release APK

## License

For personal and community use. The Bahá'í writings corpus is sourced from the Bahá'í Reference Library.
