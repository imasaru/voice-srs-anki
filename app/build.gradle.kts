import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Baked defaults. Never print these values. Both files stay outside git.
val edgeTokenFile = File("<local-directory>")
val edgeTtsToken: String = if (edgeTokenFile.isFile) edgeTokenFile.readText().trim() else ""

val ankiKeyFile = rootProject.file("local-api-key.txt")
val ankiApiKey: String = if (ankiKeyFile.isFile) ankiKeyFile.readText().trim() else ""

/** Host defaults from gitignored local.properties. Blank when missing so a fresh clone builds. */
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.isFile) {
    localPropsFile.inputStream().use { localProps.load(it) }
}
fun localProp(key: String): String = localProps.getProperty(key)?.trim().orEmpty()
val defaultAnkiUrl = localProp("voicesrs.ankiUrl")
val defaultNeuralUrl = localProp("voicesrs.neuralUrl")
val defaultAiUrl = localProp("voicesrs.aiUrl")
val defaultDecisionUrl = localProp("voicesrs.decisionUrl")

/** Java string literal. Does not log [raw]. */
fun javaStringLiteral(raw: String): String {
    val out = StringBuilder(raw.length + 2)
    out.append('"')
    for (ch in raw) {
        when (ch) {
            '\\' -> out.append("\\\\")
            '"' -> out.append("\\\"")
            '\n', '\r' -> Unit
            else -> out.append(ch)
        }
    }
    out.append('"')
    return out.toString()
}

android {
    namespace = "com.evan.voicesrs"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.evan.voicesrs"
        minSdk = 26
        targetSdk = 35
        versionCode = 23
        versionName = "0.1.22"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ANKI_API_KEY", javaStringLiteral(ankiApiKey))
        buildConfigField("String", "EDGE_TTS_TOKEN", javaStringLiteral(edgeTtsToken))
        buildConfigField("String", "DEFAULT_ANKI_URL", javaStringLiteral(defaultAnkiUrl))
        buildConfigField("String", "DEFAULT_NEURAL_URL", javaStringLiteral(defaultNeuralUrl))
        buildConfigField("String", "DEFAULT_AI_URL", javaStringLiteral(defaultAiUrl))
        buildConfigField("String", "DEFAULT_DECISION_URL", javaStringLiteral(defaultDecisionUrl))
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // org.json is bundled with Android; HttpURLConnection for AnkiConnect.
    testImplementation("junit:junit:4.13.2")
    // org.json on the JVM for unit tests:
    testImplementation("org.json:json:20240303")
    // Robolectric smoke tests: render real layouts on the JVM (no emulator).
    // 4.14.1 is the latest that supports the app's target SDK (35); 4.13 caps at 34.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
