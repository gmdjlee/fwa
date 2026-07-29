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
        // W2(F3+F4+S2+S3): IShellExec.aidl 시그니처가 바뀌었다. Shizuku.UserServiceArgs.version()
        // 이 이 값을 근거로 UserService 프로세스 재생성 여부를 결정하므로, AIDL 변경 시 버전을
        // 반드시 올려야 한다 — 안 그러면 기기에 이미 떠 있는 구 바이너리가 재사용돼
        // AbstractMethodError 가 난다. (W0 에서 1→2 로 올렸으나 그 빌드가 기기에 설치돼 있을 수
        // 있으므로, AIDL 이 실제로 바뀌는 이 커밋에서 다시 올린다.)
        versionCode = 3
        versionName = "0.4.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
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
