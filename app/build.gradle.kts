plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.chardoncs.ezmpv"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.chardoncs.ezmpv"
        minSdk = 29
        //noinspection OldTargetApi
        targetSdk = 36
        val baseVersionCode = 1
        val abiSuffix = (project.findProperty("abiVercodeSuffix") as String?)?.toInt() ?: 0
        versionCode = if (abiSuffix == 0) baseVersionCode else baseVersionCode * 10 + abiSuffix
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            val targetAbi = project.findProperty("targetAbi") as String?
            abiFilters += when (targetAbi) {
                null -> listOf("arm64-v8a", "x86_64")
                else -> listOf(targetAbi)
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = (providers.gradleProperty("EZMPV_KEYSTORE").orNull
                ?: System.getenv("EZMPV_KEYSTORE"))?.let { rootProject.file(it) }
            storePassword = providers.gradleProperty("EZMPV_KEYSTORE_PASSWORD").orNull
                ?: System.getenv("EZMPV_KEYSTORE_PASSWORD")
            keyAlias = providers.gradleProperty("EZMPV_KEY_ALIAS").orNull
                ?: System.getenv("EZMPV_KEY_ALIAS")
            keyPassword = providers.gradleProperty("EZMPV_KEY_PASSWORD").orNull
                ?: System.getenv("EZMPV_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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
    }
}

configurations.all {
    exclude(group = "io.opencensus")
}

dependencies {
    if (project.hasProperty("useLocalLibmpv")) {
        implementation(files("libs/libmpv.aar"))
    } else {
        implementation(libs.dev.jdtech.mpv.libmpv)
    }
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}