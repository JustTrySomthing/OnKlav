plugins { id("com.android.library"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "app.onklav.shared"
    compileSdk = 36
    defaultConfig { minSdk = 29; consumerProguardFiles("consumer-rules.pro") }
    buildTypes { create("diagnostic") { initWith(getByName("debug")); matchingFallbacks+=listOf("debug") } }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.all { it.inputs.file(rootProject.file("core/target/debug/libonklav_core.so")); it.systemProperty("java.library.path", rootProject.file("core/target/debug").absolutePath) } }
}
dependencies {
    implementation(files("libs/jump3r-1.0.5-core.jar"))
    api(platform("androidx.compose:compose-bom:2025.04.01"))
    api("androidx.activity:activity-compose:1.10.1")
    api("androidx.compose.material3:material3")
    api("androidx.compose.material:material-icons-core")
    api("androidx.compose.foundation:foundation")
    api("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    api("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
