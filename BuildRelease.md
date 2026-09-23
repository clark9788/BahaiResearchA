# Build and Release Workflow for Bahá'í Research

This document outlines the steps needed to prepare this Android app for a Google Play Store release using an Android App Bundle (AAB). The goal is to produce a signed, store-ready build and to complete the Play Console setup needed for publication.

## 1. Confirm app readiness

Before building a release, make sure the app is stable and the code is ready for public distribution.

Checklist:
- Confirm the branch is the release branch or a clean release candidate branch.
- Review version code and version name in app/build.gradle.
- Verify the app starts properly in debug mode on a real device.
- Confirm database initialization and search functionality work without internet access.
- Test the full result flow: filters, search, long-press actions, and source viewer.
- Verify the app works on at least one modern Android version and one older supported version.

Recommended release metadata in app/build.gradle:
- versionCode: increment for each release
- versionName: human-readable public version

## 2. Prepare the signing key

Google Play requires an upload key to sign the release bundle. Do not commit the keystore or passwords to the repo.

Generate a keystore:

```bash
keytool -genkeypair -v \
  -keystore upload-keystore.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

You will be prompted for:
- keystore password
- key password
- first and last name
- organizational unit
- organization
- city
- state/country

Important:
- Store the keystore in a secure location.
- Back it up offline.
- Keep the passwords in a password manager or secure secret store.

If Play Console asks for an upload certificate:

```bash
keytool -export -rfc -alias upload -file upload_cert.pem -keystore upload-keystore.jks
```

## 3. Configure signing in Gradle

Create a local file named keystore.properties in the project root. This file should be kept out of version control.

Example keystore.properties:

```properties
storeFile=/absolute/path/to/upload-keystore.jks
storePassword=yourStorePassword
keyAlias=upload
keyPassword=yourKeyPassword
```

Add this to .gitignore if it is not already ignored:

```gitignore
keystore.properties
*.jks
```

Then configure app/build.gradle with a release signing block.

Example configuration:

```groovy
def keystorePropertiesFile = rootProject.file("keystore.properties")
def keystoreProperties = new Properties()

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        release {
            if (keystorePropertiesFile.exists()) {
                storeFile file(keystoreProperties['storeFile'])
                storePassword keystoreProperties['storePassword']
                keyAlias keystoreProperties['keyAlias']
                keyPassword keystoreProperties['keyPassword']
            }
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

Note:
- This is the recommended secure approach for local builds.
- Do not commit the keystore or its password values.

## 4. Build the release bundle from VS Code / terminal

If you are using VS Code instead of Android Studio, use a plain shell command with an absolute path to your keystore or with environment variables set in your shell.

### Option A: use a keystore.properties file

Create a local file at the project root called keystore.properties and keep it out of source control.

Example:

```properties
storeFile=/home/your-user/.credentials/bahairesearch.jks
storePassword=yourStorePassword
keyAlias=upload
keyPassword=yourKeyPassword
```

Then run:

```bash
./gradlew :app:bundleRelease
```

This is the most portable method because it avoids any Android Studio-only variables such as `$PROJECT_DIR$`.

### Option B: use environment variables

Set environment variables before running the build:

```bash
export BAHIARESEARCH_KEYSTORE=/home/your-user/.credentials/bahairesearch.jks
export BAHIARESEARCH_KEYSTORE_PASSWORD=yourStorePassword
export BAHIARESEARCH_KEY_ALIAS=upload
export BAHIARESEARCH_KEY_PASSWORD=yourKeyPassword
```

Then build:

```bash
./gradlew :app:bundleRelease
```

If you use a custom Gradle signing setup that reads these environment variables, they will work in VS Code and in a CI environment.

The output will be in:

```text
app/build/outputs/bundle/release/app-release.aab
```

Optional: also build a release APK for local testing or direct distribution:

```bash
./gradlew :app:assembleRelease
```

APK output:

```text
app/build/outputs/apk/release/app-release.apk
```

### Why not use $PROJECT_DIR$?

`$PROJECT_DIR$` is an Android Studio / IntelliJ variable, not a system environment variable. It is resolved by the IDE, not by your shell. That means $PROJECT_DIR$ is convenient inside Android Studio configuration files, but it will not work reliably in VS Code, terminal builds, or CI automation.

For any reproducible release process, prefer one of these:
- an absolute path in keystore.properties
- shell environment variables
- a Gradle config that reads those values explicitly

## 5. Validate the build before uploading

Before releasing to Play, verify the release artifact works.

Recommended checks:
- Install the release APK on a real device.
- Confirm the app launches and data loads correctly.
- Test search, filters, and source viewer.
- Verify first launch and database initialization complete.
- Test offline mode.
- Test on more than one device if possible.

If you want to convert the AAB to a testable APK locally using bundletool:

```bash
bundletool build-apks \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=app.apks \
  --mode=universal \
  --ks=upload-keystore.jks \
  --ks-pass=pass:YOUR_STORE_PASSWORD \
  --ks-key-alias=upload \
  --key-pass=pass:YOUR_KEY_PASSWORD
```

Then install:

```bash
bundletool install-apks --apks=app.apks
```

## 6. Create the Google Play Console app

Before uploading, set up the Play Console account and app entry.

Steps:
1. Sign in to Google Play Console with a developer account.
2. Create a new app.
3. Enter app name, default language, and app type.
4. Set the package name to exactly:
   ```text
   com.bahairesearch.android
   ```
5. Complete the app registration form.

## 7. Configure Play App Signing

Google Play recommends using Play App Signing.

In Play Console:
1. Go to Setup > App integrity.
2. Choose Play App Signing.
3. Register your upload key certificate.
4. Upload the upload certificate from the keystore if prompted.

This lets Google manage the app signing key while you sign with the upload key during deployment.

## 8. Prepare the store listing

The app listing is essential for Play.

Required assets:
- App icon
- Feature graphic
- Screenshots
- Short description
- Full description
- Privacy policy URL
- Contact email

Recommended screenshots:
- Search screen with filters
- Search results list
- Long-press result actions
- Source viewer with citation
- Offline search behavior

Short description example:

```text
Offline Bahá'í search app for sacred writings, author and work filters, and source viewing.
```

Long description example:

```text
Bahá'í Research is an offline Android app for searching authoritative Bahá'í writings.

Features:
- Full-text search across thousands of sacred passages
- Filter by author and specific work
- Relevance-ranked results
- NEAR proximity search for close word combinations
- Long-press result actions for copy and source access
- Source viewer that opens the relevant passage in context
- Works fully offline without internet access

This app is designed for personal study, reference, and thoughtful research of Bahá'í texts.
```

## 9. Add legal and privacy information

Google Play requires the declarations below. A free download is not automatically exempt from copyright requirements: the app still needs permission or a valid licence for the way it redistributes the bundled corpus.

### 9.1 Privacy policy

Host a public, stable page at a URL that does not require an account or app installation, then enter that URL in Play Console. For the current app implementation, the following is a suitable starting point. Replace the bracketed values before publishing:

```text
Privacy Policy for Bahá'í Research
Effective date: [DATE]

Bahá'í Research is an offline Android application for searching and viewing a bundled corpus of Bahá'í writings. It is provided by [PUBLISHER NAME].

Data collection and sharing
Bahá'í Research does not collect, transmit, sell, or share personal information. The app does not require an account and does not use advertising, analytics, crash-reporting, or other tracking services.

Search terms, filters, and viewed passages remain on the device and are not sent to the publisher or to a server by the app. The app stores its bundled corpus and local application data in Android app storage. Android may independently back up or manage app data according to the device owner's settings and Google's services; this app does not receive that data.

Children
The app does not knowingly collect personal information from children or adults.

Changes
If the app's data practices change, this policy will be updated before or when the change is released. The effective date above will also be updated.

Contact
For privacy questions, contact [CONTACT EMAIL].
```

This wording must be revised if a future release adds ads, analytics, crash reporting, online source loading, sign-in, cloud sync, or any other data-sharing SDK. Review the merged release manifest and dependency list for every release.

### 9.2 Play Console Data safety answers

For the current build, verify these answers against the final signed artifact before submitting:

- Does your app collect or share user data? **No**, provided no additional SDK or service is added.
- Is all data collected encrypted in transit? **Not applicable**, because the app does not transmit collected data.
- Do users have a way to request deletion of data? **Not applicable**, because the app does not collect user data.
- Does the app contain ads? **No**.
- Does the app require account creation or login? **No**.

The user's deliberate use of Android's copy or share action is not the same as the app collecting that content. Do not describe Android or Google Play's own platform processing as data collected by this app.

### 9.3 Copyright and source-material notice

The bundled XHTML files, the four additional collections imported from PDF/DOCX, and `corpus.db` contain content obtained from the Bahá'í Reference Library. The four collections were handled separately only because equivalent HTML downloads were not available. Based on the terms supplied at `https://www.bahai.org/legal`, retain the source attribution and include this notice in the app's About/legal screen and on the privacy or legal page used for the listing:

```text
Copyright © Bahá'í International Community

The Bahá'í writings and related source content included in this application are provided from the Bahá'í Reference Library at https://www.bahai.org/library/ and are used subject to the terms of use at https://www.bahai.org/legal.

This application is an independent, free, non-commercial study and research tool. It is not affiliated with, sponsored by, or endorsed by the Bahá'í International Community. The application does not alter the intent, character, nature, or meaning of the source content. Individual authors are credited where named in the source.
```

Also verify before submission that:

- Every bundled document retains its source title, author where named, and source URL or other source citation.
- Search indexing, excerpts, HTML conversion, and displayed citations do not misrepresent the source or alter its meaning.
- No photographs, maps, audio, video, logos, or other third-party material is bundled without its own permission and attribution.
- The app and listing do not imply endorsement by the Bahá'í International Community.
- The publisher can stop distributing or update the app promptly if the rights holder withdraws permission.

The supplied terms state that prior permission is required for commercial use. Since this app is intended to be free and non-commercial, that specific requirement may not apply, but non-commercial status does not waive the other terms. Because this app distributes a complete offline corpus rather than merely linking to it, request written confirmation from `termsofuse@bahai.org` that this form of redistribution is acceptable, including the four non-HTML collections, before production release and keep the response with the release records. Do not describe the corpus as public domain or open source unless the rights holder confirms that status.

### 9.4 Other Play declarations

- Complete the content rating questionnaire accurately. The app is a reference/search tool and contains no user-generated content, ads, or interactive social features.
- Declare that the app has no login or restricted app access.
- Provide a support/contact email that is monitored.
- Confirm that the store description, screenshots, icon, and app name do not suggest official affiliation or endorsement.

## 10. Upload the AAB to Play Console

In Google Play Console:
1. Navigate to Release > Testing or Production.
2. Choose a track such as Internal testing or Closed testing.
3. Upload the signed AAB:
   ```text
   app/build/outputs/bundle/release/app-release.aab
   ```
4. Add release notes.
5. Save the draft.

## 11. Test in a pre-release track

Before production release:
- Use Internal testing first.
- Invite testers.
- Review crashes and install issues.
- Check Pre-launch report results.
- Fix any issues and upload a corrected AAB.

Recommended flow:
- Internal testing
- Closed testing
- Open testing
- Production rollout

## 12. Production rollout

After testing is complete:
1. Promote the release to Production.
2. Use staged rollout if desired (for example 10%, then 50%, then 100%).
3. Monitor crash reports and user feedback.
4. Keep a release changelog for future updates.

## 13. Maintenance and future releases

Plan for ongoing app maintenance.

Recommended future tasks:
- Update versionCode and versionName for each release.
- Keep signing keystore secure and backed up.
- Review app behavior after Android OS updates.
- Track analytics or crash metrics.
- Improve update handling for the corpus database if a new corpus version is introduced.
- Consider adding a proper version-check mechanism for bundled corpus updates.

## 14. Suggested release checklist

Use this as a final pre-upload checklist:

- [ ] Release candidate branch is ready
- [ ] versionCode/versionName are set
- [ ] keystore created and backed up
- [ ] keystore.properties created locally and excluded from git
- [ ] app/build.gradle signing config added
- [ ] release build succeeds
- [ ] bundleRelease succeeds
- [ ] AAB generated successfully
- [ ] release APK installed and tested on device
- [ ] privacy policy exists
- [ ] privacy policy URL entered in Play Console
- [ ] Data Safety answers verified against the release artifact
- [ ] copyright and source-material notice published in the app/legal page
- [ ] source titles, authors, citations, and URLs checked
- [ ] written non-commercial redistribution confirmation requested from termsofuse@bahai.org
- [ ] Play Console app created
- [ ] package name matches
- [ ] store listing complete
- [ ] screenshots uploaded
- [ ] app icon and feature graphic added
- [ ] Data Safety form completed
- [ ] content rating completed
- [ ] AAB uploaded to Play Console
- [ ] internal testing pass completed
- [ ] production release notes ready

## 15. Important note about local environment and dependencies

This project currently references a local sibling project in settings.gradle:

```groovy
includeBuild('../BahaiSearchCommon')
```

On machines without that sibling repo available, the build may fail. For portability across developer machines and CI, consider guarding the includeBuild check so it only runs when the directory exists.

Example guarded approach:

```groovy
File localSearchCommon = file('../BahaiSearchCommon')
if (localSearchCommon.exists()) {
    includeBuild(localSearchCommon)
}
```

This preserves local development while letting builds continue in environments where the library is available as a published artifact instead of a local build.

## 16. Recommended next milestone

The next milestone after this setup is:
- Create keystore
- Configure signing
- Run bundleRelease
- Upload to Internal Testing
- Validate app on real devices
- Promote to Production once validated

This keeps the release process disciplined and reduces the risk of publishing a broken app.
