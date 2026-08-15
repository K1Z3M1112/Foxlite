plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    namespace = "com.nativewinruntime"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nativewinruntime"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0"
    }
    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.30.5" }
    }
    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*")
    }
    // Runtime assets (rootfs.tzst, dxvk/vkd3d/turnip archives, soundfont, .icp
    // control profiles) are already compressed. Storing them uncompressed in
    // the APK avoids a slow, pointless re-compress pass at build time and
    // avoids double-compression bloat.
    androidResources {
        noCompress += listOf("tzst", "sf2", "icp")
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("com.github.luben:zstd-jni:1.5.7-6")
}


kotlin {
    jvmToolchain(17)
}
