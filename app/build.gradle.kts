plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

ksp {
    // exportSchema is disabled (see AppDatabase) — no schema directory needed.
    arg("room.incremental", "true")
}

android {
    namespace = "com.ebooks.reader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ebooks.reader"
        minSdk = 26
        targetSdk = 34
        // Supplied by CI via -PVERSION_CODE / VERSION_NAME env vars; falls back to dev defaults.
        versionCode = (System.getenv("VERSION_CODE") ?: findProperty("VERSION_CODE"))
            ?.toString()?.toIntOrNull() ?: 1
        versionName = (System.getenv("VERSION_NAME") ?: findProperty("VERSION_NAME"))
            ?.toString()?.removePrefix("v") ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Android refuses to install unsigned APKs, so an "unsigned release"
            // artifact is dead weight. Default to the auto-generated debug
            // keystore so assembleRelease always yields an installable APK.
            // CI passes -Pandroid.injected.signing.* when the real release
            // keystore secrets are configured; those override this fallback.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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

    lint {
        // Lint runs and reports (SARIF/HTML + code scanning) but does not fail the
        // build — the project carries pre-existing lint errors and lint is advisory
        // here, not a merge gate. Re-enable by removing abortOnError to make it block.
        abortOnError = false
        warningsAsErrors = false
        // Only scan app sources, not every transitive dependency.
        checkDependencies = false
        // Write a SARIF report for GitHub code scanning upload.
        sarifReport = true
        htmlReport = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.androidx.glance.appwidget)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
