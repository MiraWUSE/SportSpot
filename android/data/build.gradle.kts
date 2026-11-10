import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

val localConfiguration =
    Properties().apply {
        val configurationFile = rootProject.file("local.properties")
        if (configurationFile.exists()) {
            configurationFile.inputStream().use { load(it) }
        }
    }
val publishableKey =
    providers
        .environmentVariable("SUPABASE_PUBLISHABLE_KEY")
        .orElse(localConfiguration.getProperty("supabase.publishableKey", ""))
        .get()
require(publishableKey.isEmpty() || publishableKey.matches(Regex("sb_publishable_[A-Za-z0-9_-]+"))) {
    "Configure only a Supabase publishable key"
}

android {
    namespace = "com.mirawuse.sportspot.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$publishableKey\"")
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
