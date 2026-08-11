plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    // Namespace stays as-is (it's just the R-class package used inside the
    // code, not the installed app ID) so none of the existing `package
    // com.example.mybible...` declarations need to change.
    namespace = "com.example.mybible"
    compileSdk = 35

    defaultConfig {
        // Distinct from the Capacitor app's id (com.mybible.app) so both can
        // be installed on the same device at once — Android treats
        // applicationId as the unique app identity, unrelated to namespace.
        applicationId = "com.mybible.kotlin"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Populated from environment variables (CI) or gradle.properties
        // (local machine) — never hardcode keystore credentials here.
        // Names match what .github/workflows/*.yml passes as env vars, so
        // both local and CI builds use the same three names.
        // Local: add KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD to your
        // (gitignored) ~/.gradle/gradle.properties or local
        // gradle.properties, or export them as env vars before running a
        // release build.
        create("release") {
            val envStorePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: project.findProperty("KEYSTORE_PASSWORD") as String?
            val envKeyAlias = System.getenv("KEY_ALIAS")
                ?: project.findProperty("KEY_ALIAS") as String?
            val envKeyPassword = System.getenv("KEY_PASSWORD")
                ?: project.findProperty("KEY_PASSWORD") as String?

            if (envStorePassword != null && envKeyAlias != null && envKeyPassword != null) {
                storeFile = file("${rootDir}/keystore/release.keystore")
                storePassword = envStorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
            }
            // If credentials aren't supplied, this signingConfig is left
            // unconfigured and the release build falls back to being
            // unsigned locally — CI always supplies the env vars above.
        }
    }

    buildTypes {
        debug {
            // Gives debug builds a separate applicationId
            // (com.mybible.kotlin.debug) so they install as a completely
            // different app from release rather than colliding with it —
            // Android identifies an installed app by (applicationId,
            // signing cert), and debug/release are always signed with
            // different certs (Android Studio's auto-generated debug
            // keystore vs. your release.keystore), so without this suffix
            // installing one over the other fails with a signature
            // mismatch instead of a clean install/update.
            applicationIdSuffix = ".debug"
            // App label shows "My Bible Dev" so it's visually distinct from
            // the release install on the launcher/app switcher, in addition
            // to the two already having different applicationIds above.
            resValue("string", "app_name", "My Bible (Dev)")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // English font picker in Settings (Lora, EB Garamond, Merriweather,
    // Playfair Display, Gelasio) now uses fonts bundled directly under
    // res/font/ — see ui/theme/AppFonts.kt — instead of Google's
    // downloadable-font provider, which required Play Services + network
    // and silently fell back to the system font when either was missing.
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Room: local database for the bundled/downloaded Bible text, replacing
    // per-chapter live fetches with indexed offline queries.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Home screen widget (VerseOfDayWidget) — Compose-style DSL for
    // AppWidgets, replaces the old RemoteViews-based BibleWidgetProvider.
    implementation("androidx.glance:glance-appwidget:1.1.1")

    // Periodic background Google Drive backup (DriveSyncWorker) — daily
    // sync while Settings > "Auto backup" is on, using the same
    // download-merge-upload flow as the manual Back Up button.
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Google Drive backup/sync — Sign-In UI + OAuth2 token retrieval only.
    // Deliberately NOT depending on play-services-drive or the Drive Java
    // API client: DriveBackupManager talks to the Drive v3 REST API
    // directly over HttpURLConnection, which is all three endpoints
    // (list/create/update against the appDataFolder) actually need.
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    testImplementation("junit:junit:4.13.2")
}
