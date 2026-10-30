plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "net.clagon.shelfg"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.clagon.shelfg"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.6.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Key material is supplied at build time; it is never committed to git.
    val signingKeystore = providers.environmentVariable("SHELFG_SIGNING_KEYSTORE").orNull
    val signingPassword = providers.environmentVariable("SHELFG_SIGNING_PASSWORD").orNull
    signingConfigs {
        if (!signingKeystore.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
            create("distribution") {
                storeFile = file(signingKeystore)
                storePassword = signingPassword
                keyAlias = "shelfg"
                keyPassword = signingPassword
            }
        }
    }
    buildTypes {
        release {
            signingConfigs.findByName("distribution")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
