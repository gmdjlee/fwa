plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.dj.foldwindow"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.dj.foldwindow"
        minSdk = 30              // takeScreenshot() 이 API 30+
        targetSdk = 36           // Android 16 적응형 동작을 적극 활용
        versionCode = 1
        versionName = "0.1.0-phase0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        aidl = true // P4-1: Shizuku UserService(IShellExec.aidl) 사용을 위해 AGP 8+ 에서 명시 활성화
        buildConfig = true // P4-1: ShizukuShell 이 BuildConfig.APPLICATION_ID/DEBUG/VERSION_CODE 를 참조
    }

    sourceSets {
        // config/window_profiles.json 이 SSOT. 복제하지 않고 assets 로 직접 노출한다.
        named("main") { assets.srcDirs("../config") }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.window)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
