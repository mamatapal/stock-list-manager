import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Developer settings (never committed): local.properties may contain
//   api.baseUrl=http://192.168.1.20:8080/   (your PC's LAN address, for a real phone)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Release signing (never committed): keystore.properties with storeFile, storePassword, keyAlias, keyPassword
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val releaseUrlMissing = "https://api.example.invalid/"

android {
    namespace = "app.stocklist.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.stocklist.manager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "Stock List Manager"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 10.0.2.2 is the PC as seen from the Android emulator.
            val url = localProps.getProperty("api.baseUrl") ?: "http://10.0.2.2:8080/"
            buildConfigField("String", "API_BASE_URL", "\"$url\"")
        }
        // Two extra debug installs with their own app id, data and device id, so one owner and two staff
        // can be signed in side by side on a single emulator ("separate app installations").
        create("devB") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".devb"
            manifestPlaceholders["appLabel"] = "SLM Phone B"
            matchingFallbacks += listOf("debug")
        }
        create("devC") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".devc"
            manifestPlaceholders["appLabel"] = "SLM Phone C"
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The production server address: api.baseUrl in keystore.properties, or STOCKLIST_API_URL. Must be https.
            val url = keystoreProps.getProperty("api.baseUrl") ?: System.getenv("STOCKLIST_API_URL") ?: releaseUrlMissing
            buildConfigField("String", "API_BASE_URL", "\"$url\"")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        // The release build exactly (R8 minified, resources shrunk, not debuggable), but signed with the debug key
        // and pointed at the PC's backend, to test the minified app end to end before the real release.
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            manifestPlaceholders["appLabel"] = "SLM Staging"
            signingConfig = signingConfigs.getByName("debug")
            val url = localProps.getProperty("api.baseUrl") ?: "http://10.0.2.2:8080/"
            buildConfigField("String", "API_BASE_URL", "\"$url\"")
            matchingFallbacks += listOf("release")
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
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// A release APK must be signed with your key and point at your HTTPS server; refuse to build one that is not.
gradle.taskGraph.whenReady {
    if (allTasks.any { it.name.startsWith("assembleRelease") || it.name.startsWith("bundleRelease") }) {
        val url = keystoreProps.getProperty("api.baseUrl") ?: System.getenv("STOCKLIST_API_URL") ?: releaseUrlMissing
        require(url != releaseUrlMissing && url.startsWith("https://") && url.endsWith("/")) {
            "Release build: set api.baseUrl=https://your-server/ in android/keystore.properties (see docs/RELEASE.md)"
        }
        require(keystoreProps.getProperty("storeFile") != null) {
            "Release build: create android/keystore.properties with your signing key (see docs/RELEASE.md)"
        }
    }
}

// devB/devC reuse the debug-only network settings (plain http to the PC's development backend).
android.sourceSets.getByName("devB").res.srcDir("src/debug/res")
android.sourceSets.getByName("devC").res.srcDir("src/debug/res")

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Room migration tests read the exported schemas.
android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)

    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.work.testing)
}
