plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}
android {
    namespace = "com.xuesui.englishapp.modelprobe"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.xuesui.englishapp.modelprobe"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "0.5.0-probe"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildFeatures { compose = true }
    sourceSets.getByName("main").kotlin.srcDir(rootProject.file("../../ui"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources { noCompress += "gguf" }
}
dependencies {
    implementation(project(":runtime"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
}
