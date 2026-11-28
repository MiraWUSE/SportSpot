plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)

    // Dependency Injection
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)

    // Проверка стиля Kotlin
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.mirawuse.sportspot"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mirawuse.sportspot"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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

tasks.withType<Test>().configureEach {
    inputs.files(
        "src/main/AndroidManifest.xml",
        "src/main/res/xml/backup_rules.xml",
        "src/main/res/xml/data_extraction_rules.xml",
    )
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":presentation"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
