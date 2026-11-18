import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Developer settings (never committed): local.properties may contain api.baseUrl=https://your-server/
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Release signing (never committed): keystore.properties with storeFile, storePassword, keyAlias, keyPassword, api.baseUrl.
// The Supplier Management app has its own signing key, separate from Stock List Manager's.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val releaseUrlMissing = "https://api.example.invalid/"

android {
    namespace = "app.stocklist.supplier"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.stocklist.supplier"
        minSdk = 26
        targetSdk = 35
        // Release builds on GitHub pass -PappVersionCode so every new APK installs as an update.
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "1.0.0"
        manifestPlaceholders["appLabel"] = "Supplier Management"
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
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val url = keystoreProps.getProperty("api.baseUrl") ?: System.getenv("SUPPLIER_API_URL") ?: releaseUrlMissing
            buildConfigField("String", "API_BASE_URL", "\"$url\"")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        // The release build exactly (R8 minified), signed with the debug key, to test the minified app.
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            manifestPlaceholders["appLabel"] = "Supplier Mgmt Staging"
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
        // Robolectric screen tests need the app's resources and the test activity of ui-test-manifest.
        unitTests.isIncludeAndroidResources = true
    }
}

// A release APK must be signed with its own key and point at the HTTPS server; refuse to build one that is not.
gradle.taskGraph.whenReady {
    if (allTasks.any { it.name.startsWith("assembleRelease") || it.name.startsWith("bundleRelease") }) {
        val url = keystoreProps.getProperty("api.baseUrl") ?: System.getenv("SUPPLIER_API_URL") ?: releaseUrlMissing
        require(url != releaseUrlMissing && url.startsWith("https://") && url.endsWith("/")) {
            "Release build: set api.baseUrl=https://your-server/ in supplier-android/keystore.properties"
        }
        require(keystoreProps.getProperty("storeFile") != null) {
            "Release build: create supplier-android/keystore.properties with the Supplier Management signing key"
        }
    }
}

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
    // Screen tests run on the JVM with Robolectric (test only; nothing of this is in the app).
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Failed tests show their full message and stack trace in the build log (the CI log is often all there is).
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
