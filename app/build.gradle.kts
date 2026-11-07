import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Local release and debug builds share the same persistent key as CI. The
// properties file and keystore stay outside version control; CI signs the
// unsigned release artifact in a separate step using encrypted GitHub secrets.
val sharedKeystorePropertiesFile = rootProject.file("keystore.properties")
val sharedKeystoreProperties = Properties().apply {
    if (sharedKeystorePropertiesFile.isFile) {
        sharedKeystorePropertiesFile.inputStream().use(::load)
    }
}
val hasSharedKeystore = sharedKeystorePropertiesFile.isFile

android {
    namespace = "com.akira.island"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.akira.island"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.7.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Keystore credentials stay outside Git. CI assembles unsigned and signs in a
    // separate step; local debug and release builds use the same key from the
    // ignored keystore.properties file when it is present.
    signingConfigs {
        if (hasSharedKeystore) {
            create("shared") {
                storeFile = file(sharedKeystoreProperties.getProperty("storeFile"))
                storeType = sharedKeystoreProperties.getProperty("storeType", "PKCS12")
                storePassword = sharedKeystoreProperties.getProperty("storePassword")
                keyAlias = sharedKeystoreProperties.getProperty("keyAlias")
                keyPassword = sharedKeystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (hasSharedKeystore) signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSharedKeystore) signingConfig = signingConfigs.getByName("shared")
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
    }
}

dependencies {
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.palette:palette-ktx:1.0.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
