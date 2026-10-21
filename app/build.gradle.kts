import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Baked defaults. Never print these values. Both files stay outside git.
val edgeTokenFile = File("<local-directory>")
val edgeTtsToken: String = if (edgeTokenFile.isFile) edgeTokenFile.readText().trim() else ""

val ankiKeyFile = rootProject.file("local-api-key.txt")
val ankiApiKey: String = if (ankiKeyFile.isFile) ankiKeyFile.readText().trim() else ""

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
        versionCode = 11
        versionName = "0.1.10"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ANKI_API_KEY", javaStringLiteral(ankiApiKey))
        buildConfigField("String", "EDGE_TTS_TOKEN", javaStringLiteral(edgeTtsToken))
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
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // org.json is bundled with Android; HttpURLConnection for AnkiConnect.
    testImplementation("junit:junit:4.13.2")
    // org.json on the JVM for unit tests:
    testImplementation("org.json:json:20240303")
}
