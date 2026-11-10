plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace="app.onklav.games";compileSdk=36
    defaultConfig {minSdk=29;targetSdk=35;ndk {abiFilters+=providers.gradleProperty("onklavAbis").orElse("arm64-v8a").get().split(",")}}
    compileOptions {sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17};kotlinOptions {jvmTarget="17"}
    androidResources {ignoreAssetsPattern="!.svn:!.git:!.gitignore:!.ds_store:!*.scc:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~"}
    packaging {jniLibs {useLegacyPackaging=true}}
}
dependencies {
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.activity:activity-ktx:1.10.1")
}
