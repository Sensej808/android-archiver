plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        compose = true
    }

    // Настройки Compose
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

}

dependencies {


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.androidx.activity.compose) // Для поддержки Compose в Activity
    implementation(libs.androidx.compose.ui) // Основная библиотека Compose
    implementation(libs.androidx.compose.material3) // Material Design 3 компоненты
    implementation(libs.androidx.compose.ui.tooling.preview) // Поддержка предпросмотра
    debugImplementation(libs.androidx.compose.ui.tooling) // Инструменты для отладки
    implementation(libs.androidx.compose.runtime.livedata) // Интеграция с LiveData (опционально)
    implementation(libs.androidx.navigation.compose) // Навигация в Compose (опционально)

}