import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Keep the release key and its passwords out of version control. The file is gitignored; the
// keystore itself lives under the user's AndroidKeys directory.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.reader(Charsets.UTF_8).use { load(it) }
    }
}

// Only debug builds honour these overrides, for the local end-to-end update check.
val debugUpdateApiBase =
    providers.gradleProperty("kuiesUpdateApiBase").getOrElse("https://api.github.com")
val debugUpdateAssetBase = providers.gradleProperty("kuiesUpdateAssetBase")
    .getOrElse("https://github.com/kuies001/KuiesLink/releases/download/")

android {
    namespace = "tw.kuies.link"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "tw.kuies.link"
        minSdk = 28
        targetSdk = 37
        versionCode = 11
        versionName = "0.3.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.isFile) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "UPDATE_API_BASE", "\"$debugUpdateApiBase\"")
            buildConfigField("String", "UPDATE_ASSET_BASE", "\"$debugUpdateAssetBase\"")
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            buildConfigField("String", "UPDATE_API_BASE", "\"https://api.github.com\"")
            buildConfigField(
                "String",
                "UPDATE_ASSET_BASE",
                "\"https://github.com/kuies001/KuiesLink/releases/download/\"",
            )
            // R8 full-mode optimisation broke OkHttp/Kotlin (IllegalAccessError at launch).
            // Keep the release unminified for v0.3.3; revisit with keep rules later.
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}