# Voice SRS

Voice SRS is an experimental Android app for hands-free flashcard practice with Anki and AnkiConnect. It supports speech input/output and optional OpenAI-compatible AI explanations. It is maintained at the author's own pace, with no promised support or release schedule.

## Build and test

Use JDK 17 and Android SDK 36. Configure your Android SDK through the standard environment or ignored `local.properties` file, then run:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`. See [BUILD_NOTES.md](BUILD_NOTES.md) for build requirements.

## Setup and safety

Install Anki and AnkiConnect separately and configure the connection URL and credentials in the app's Settings. Configure any optional speech or AI services there as well. No deployment infrastructure or credentials are included. Use HTTPS or another trusted protected connection; never expose AnkiConnect directly to the internet. This experimental app permits HTTP and its backup/platform-compatibility policies need further review before general distribution.

## Test data and security checks

Daily-study and conversation fixtures and the speech-regression examples use invented data; see [fixture provenance](app/src/test/resources/README.md). Some other rendering/parser tests use public study-question examples and retain their upstream attribution. Never substitute personal account dumps for test fixtures.

See [security checks](tools/security/README.md) for secret scans and dependency-audit commands. Passing these checks is not a guarantee against every vulnerability or privacy issue.

## License and status

A top-level project license has not been selected. Vendored AnkiConnect code and public sample material retain their own upstream notices; those notices do not license the Android project as a whole. No publication or production-readiness claim is implied.
