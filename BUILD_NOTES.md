# Building Voice SRS

- JDK 17.
- Android SDK platform 36 and the build tools selected by the Android Gradle plugin.
- Gradle is supplied through the checked-in wrapper; dependency versions are declared in the Gradle build files.

Configure the SDK using the standard Android environment variables or ignored `local.properties` file. A fresh source checkout builds without service credentials. Connection settings are supplied through the app UI.

```sh
./gradlew clean testDebugUnitTest assembleDebug --no-build-cache
```

Unit tests include JVM/Robolectric checks and source/layout regression checks. A passing debug build is not a substitute for device testing across supported Android versions. Existing platform-compatibility lint findings need separate review.

Do not commit credentials, generated APKs, personal study data, local settings or build outputs.
