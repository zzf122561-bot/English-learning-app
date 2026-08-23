import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val signingPropertiesFile = rootProject.file("_manager/local/signing.properties")
check(signingPropertiesFile.isFile) {
    "Missing manager-owned signing configuration: ${signingPropertiesFile.absolutePath}"
}
val signingProperties = Properties().apply {
    signingPropertiesFile.inputStream().use(::load)
}

android {
    namespace = "com.xuesui.englishapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xuesui.englishapp"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.1"
    }

    signingConfigs {
        create("personal") {
            storeFile = file(signingProperties.getProperty("storeFile"))
            storePassword = signingProperties.getProperty("storePassword")
            keyAlias = signingProperties.getProperty("keyAlias")
            keyPassword = signingProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("personal")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":feature-01-word-memory"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
