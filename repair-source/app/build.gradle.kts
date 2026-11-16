plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.altyazi"
    compileSdk = 35

    defaultConfig {
        // PAKET KİMLİĞİ: com.example.altyazi — namespace ile AYNI.
        //
        // Sonuç: her yeni sürüm telefondaki uygulamanın ÜSTÜNE kurulur.
        // Ayarlar, "Sözlüğüm" girdileri ve çeviri hafızası korunur; ikinci
        // bir simge çıkmaz. versionCode her sürümde arttığı ve imza sabit
        // olduğu için Android bunu güncelleme sayar.
        //
        // (Burada eskiden "eski uygulamanın yanına ayrı kurulsun" diyen bir
        // yorum vardı; kimlik namespace ile aynı olduğu için doğru değildi.
        // Ayrı kalması istenirse bu satır değiştirilmeli, örneğin
        // "com.example.altyazi.yeni" — o zaman iki simge olur ve yeni
        // uygulama boş ayarlarla başlar.)
        //
        // TV uygulaması ayrı kimlik kullanıyor (com.example.altyazi.tv),
        // bu yüzden telefon sürümüyle çakışmaz; ikisi yan yana durur.
        applicationId = "com.example.altyazi"
        minSdk = 31
        targetSdk = 34
        versionCode = 205
        versionName = "10.42.7-repair205"
    }

    // Sabit imza: her derleme aynı anahtarla imzalanır, güncelleme eskisinin üstüne kurulur.
    // Debug APK uses Android's default per-run signing key.
    // No key or password is stored in this source archive.

    buildTypes {
        debug {
            isMinifyEnabled = false
            // Install next to stable 201; no compromised update key is required.
            applicationIdSuffix = ".benchmark"
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        viewBinding = false
        // 10.2: Firefox çeviri motoru arayüzü (src/main/aidl)
        aidl = true
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
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:translate:17.0.2")

    // Telefonda çalışan yapay zekâ (Gemma) — internetsiz çeviri.
    // Kotlin 2.4 ile derlenmiş; bu yüzden projede Kotlin 2.3 (bir sonraki sürümü okuyabilir).
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Direct on-device Marian OPUS-MT ONNX inference, model weights imported separately.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
}
