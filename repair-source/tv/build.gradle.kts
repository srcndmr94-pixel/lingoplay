// GameLingo TV — Android TV'nin kendisine kurulan AYRI uygulama.
// Kodun ve tasarımın tamamı telefon uygulamasıyla ORTAK (../app/src/main);
// burada yalnızca TV'ye özel olanlar var: paket adı, eski Android desteği,
// TV ana ekranı (leanback) manifesti ve afiş.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Aynı namespace: ortak kod com.example.altyazi.R'yi kullanıyor.
    namespace = "com.example.altyazi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.altyazi.tv"   // telefondaki GameLingo'dan ayrı uygulama
        minSdk = 28                                // Android 9 ve üstü TV'ler
        targetSdk = 34
        versionCode = 8
        versionName = "TV 2.0"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }   // TV'lerin çoğu 32 bit ARM
    }

    // Debug APK uses Android's default per-run signing key.
    // No key or password is stored in this source archive.

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.srcDirs("../app/src/main/java")
            res.srcDirs("../app/src/main/res", "src/main/res")
        }
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs { pickFirsts.add("**/liblitertlm_jni.so") }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:translate:17.0.2")
    // Ortak kod derlensin diye var; TV'de model indirilmez, yapay zekâ hiç yüklenmez.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
