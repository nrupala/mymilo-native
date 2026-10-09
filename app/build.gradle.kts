plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}android {
    namespace = "org.aimlds.mymilo"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.aimlds.mymilo"
        minSdk = 26
        targetSdk = 34
        // CI passes the workflow run number; versionCode = 500 + N so
        // every published build upgrades cleanly and the app can map
        // its versionCode back to the GitHub release number (build-N).
        val buildNumber = System.getenv("MYMILO_BUILD_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 500 + buildNumber
        buildConfigField("int", "BUILD_NUMBER", "${500 + buildNumber}")
        versionName = "0.8.0"
    }

    // Release signing (v0.6.0): the keystore is provided by CI from
    // repository secrets (never committed). Without the env vars the
    // release build is unsigned and CI fails at packaging — by design.
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("MYMILO_KEYSTORE")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storeType = "PKCS12"
                storePassword = System.getenv("MYMILO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MYMILO_KEY_ALIAS")
                keyPassword = System.getenv("MYMILO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (System.getenv("MYMILO_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    // Local database (sessions, messages, skills cache, facts)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Networking (server escalation + sync)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Background sync
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
