import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val appConfig = Properties().apply {
    val f = file("config.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val adsEnabled = appConfig.getProperty("ADS_ENABLED")?.trim() == "true"
val startIoAppId = (appConfig.getProperty("START_IO_APP_ID") ?: "").trim()

android {
    namespace = "com.tvseries.Movies"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tvseries.Movies"
        minSdk = 26
        targetSdk = 34
        // Every CI build gets a higher versionCode (run number) so it installs OVER the previous one.
        val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = 100 + buildNumber
        versionName = "1.10.0.$buildNumber"
        buildConfigField("int", "BUILD_NUMBER", buildNumber.toString())

        // The TMDB key is NEVER stored in the repository. It is read from a Gradle property
        // or the TMDB_API_KEY environment variable (GitHub Actions secret). Empty = the user
        // enters the key inside the app (Settings).
        val tmdbKey = (project.findProperty("TMDB_API_KEY") as String?)
            ?: System.getenv("TMDB_API_KEY")?.takeIf { it.isNotBlank() }
            ?: appConfig.getProperty("TMDB_API_KEY")
            ?: ""
        buildConfigField("String", "TMDB_API_KEY", "\"${tmdbKey.trim()}\"")
        // Ads are prepared but disabled. No ad SDK is included in this build.
        buildConfigField("boolean", "ADS_ENABLED", adsEnabled.toString())
        manifestPlaceholders["startIoAppId"] = startIoAppId
        buildConfigField("String", "UPDATE_REPO", "\"${(appConfig.getProperty("UPDATE_REPO") ?: "").trim()}\"")
        buildConfigField("String", "PRIVACY_URL", "\"${(appConfig.getProperty("PRIVACY_URL") ?: "").trim()}\"")
        buildConfigField("String", "START_IO_APP_ID", "\"${(appConfig.getProperty("START_IO_APP_ID") ?: "").trim()}\"")
    }

    signingConfigs {
        // One fixed key for all test builds. Android only updates an installed app when the signature matches;
        // CI debug keys are random per run, which is why every update used to need an uninstall.
        getByName("debug") {
            val fixed = file("signing/movies-test.keystore")
            if (fixed.exists()) {
                storeFile = fixed
                storePassword = "moviestest123"
                keyAlias = "moviestest"
                keyPassword = "moviestest123"
            }
        }
        create("release") {
            val path = System.getenv("KEYSTORE_PATH")
            if (!path.isNullOrBlank() && file(path).exists()) {
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Real key only when the CI provides one (KEYSTORE_PATH...); otherwise an installable debug-signed test build.
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir(if (adsEnabled) "src/startio/java" else "src/noads/java")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    if (adsEnabled) implementation("com.startapp:inapp-sdk:4.+")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
