plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.leandrozzy.gamecoach"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.leandrozzy.gamecoach"
        minSdk = 24; targetSdk = 34; versionCode = 1; versionName = "0.1"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore"); storePassword = "android"
            keyAlias = "androiddebugkey"; keyPassword = "android"
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
