import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val googleClientId = localProperties.getProperty("GOOGLE_CLIENT_ID") ?: "\"YOUR_WEB_CLIENT_ID_HERE\""

// Personal/sideloading release signing — NOT suitable for Play Store distribution.
// Generate with (see README/SIGNING.md for the full command):
//   keytool -genkeypair -v -keystore release.keystore -alias budgetpace -keyalg RSA \
//     -keysize 2048 -validity 10000
// then add to local.properties (which is gitignored, same as GOOGLE_CLIENT_ID):
//   RELEASE_STORE_FILE=../release.keystore
//   RELEASE_STORE_PASSWORD=...
//   RELEASE_KEY_ALIAS=budgetpace
//   RELEASE_KEY_PASSWORD=...
// Until those are set, `release` builds fall back to being unsigned, same as before.
val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE")
val releaseStorePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "com.budgetpace.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.budgetpace.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GOOGLE_CLIENT_ID", googleClientId)
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            // No applicationIdSuffix: Google Sign-In and the Sheets/Drive Authorization API
            // validate the calling app's package name (+ signing SHA-1) against the OAuth
            // client registered in Google Cloud Console. A ".debug"-suffixed package here would
            // silently mismatch whatever package was actually registered there, breaking both
            // flows with an opaque error — keep debug and release on the exact same package name
            // so only the SHA-1 differs between them.
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Desugaring for java.time on older APIs
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // collectAsStateWithLifecycle / LifecycleResumeEffect — used to refresh permission state when
    // the owner returns from system Settings, and to catch a month rollover on every foreground.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    // Holds the system splash until we know whether onboarding already ran, so the first frame is
    // the right screen rather than a white flash followed by a spinner.
    implementation(libs.androidx.core.splashscreen)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    // Bridges Play Services Task<T> (used by the Google Authorization API) to suspend fun/.await()
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)

    // Instrumented Tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
}

dependencies {
    implementation("androidx.credentials:credentials:1.3.0-rc01")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0-rc01")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // Authorization API: requests the drive.file/Sheets scope as a step separate from
    // Credential Manager sign-in, per spec §7. 22.0.0 is the floor, not a preference:
    // AuthorizationClient.clearToken(ClearTokenRequest) — how a stale token is dropped after a
    // Sheets 401 — does not exist before 21.4.0.
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    // No androidx.security here on purpose: EncryptedSharedPreferences can throw for the whole
    // life of an install once the Keystore entry is lost (a restore, a lock-screen change), and
    // it stores nothing here that is worth that risk — an email address and two booleans. The
    // tokens themselves are never persisted by this app; Play services holds them.
}

dependencies {
    implementation("com.google.apis:google-api-services-sheets:v4-rev20230815-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    // Explicit: don't rely on google-api-client's transitive JSON factory resolution for GsonFactory.
    implementation("com.google.http-client:google-http-client-gson:1.44.1")
}
