plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace="com.gameaicoach"; compileSdk=35
 defaultConfig { applicationId="com.gameaicoach"; minSdk=26; targetSdk=35; versionCode=12; versionName="1.1.0" }
 signingConfigs {
  create("coach") {
   storeFile=file("../coach-release.keystore")
   storePassword="GameAICoach2026"
   keyAlias="coach"
   keyPassword="GameAICoach2026"
  }
 }
 buildTypes {
  debug { signingConfig=signingConfigs.getByName("coach") }
  release { isMinifyEnabled=false; signingConfig=signingConfigs.getByName("coach"); proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro") }
 }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
}
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.appcompat:appcompat:1.7.0")
 implementation("com.google.android.material:material:1.12.0")
 implementation("com.google.mlkit:text-recognition:16.0.1")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
