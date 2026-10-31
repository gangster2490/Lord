plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rob.neon"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rob.neon"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        @Suppress("DEPRECATION") resourceConfigurations += listOf("ru", "de")
    }

    buildTypes {
        // Test build is signed with the Android *debug* key (generated locally, never committed).
        debug { applicationIdSuffix = "" }
        release {
            isMinifyEnabled = false
            // No release keystore lives in the repository. Sign externally (see docs/BUILD.md).
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    androidResources { noCompress += listOf("jpg", "json", "xml") }
    testOptions { unitTests { isIncludeAndroidResources = true; all { it.systemProperty("rob.shots", System.getProperty("rob.shots") ?: ""); it.jvmArgs("-Xmx2g") } } }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation(bom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
