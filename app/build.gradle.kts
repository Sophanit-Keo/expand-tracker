import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.room)
}

/*
 * Upload key for Google Play. Read from keystore.properties in the project root (never committed,
 * see docs/PUBLISHING.md) or, on CI, from UPLOAD_* environment variables. Without it, release builds
 * fall back to the debug key: fine for installing on your own phone, rejected by Google Play.
 */
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(property: String, env: String): String? = keystoreProperties.getProperty(property) ?: System.getenv(env)
val uploadStoreFile: String? = signingValue("storeFile", "UPLOAD_STORE_FILE")

android {
    namespace = "com.expensetracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.expensetracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.3.1"
    }

    signingConfigs {
        if (uploadStoreFile != null) {
            create("upload") {
                storeFile = rootProject.file(uploadStoreFile)
                storePassword = signingValue("storePassword", "UPLOAD_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "UPLOAD_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (uploadStoreFile != null) {
                signingConfigs.getByName("upload")
            } else {
                logger.warn("Release build is signed with the DEBUG key. Google Play won't accept it; see docs/PUBLISHING.md.")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    // Exported schemas let future versions verify database upgrades.
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    // Reads text (with positions) from PDF bank statements.
    implementation(libs.pdfbox.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    // Screenshot tests: render real screens on the JVM (./gradlew recordRoborazziDebug).
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
