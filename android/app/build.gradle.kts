plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
val onklavAbis = providers.gradleProperty("onklavAbis").orElse("arm64-v8a").get().split(",")
require(onklavAbis.isNotEmpty() && onklavAbis.all { it in setOf("arm64-v8a", "x86_64") }) { "Use arm64-v8a and/or x86_64 for onklavAbis" }
android {
    namespace="app.onklav.home"
    ndkVersion="27.2.12479018"
    compileSdk=36
    defaultConfig { applicationId="app.onklav.home.prototype";minSdk=29;targetSdk=36;versionCode=228;versionName="0.101.49-rc1";testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner";ndk {abiFilters+=onklavAbis} }
    buildFeatures {compose=true;buildConfig=true}
    androidResources { noCompress += "voicepack" }
    compileOptions {sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17}
    kotlinOptions {jvmTarget="17"}
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("onklav.project.root", rootProject.projectDir.absolutePath)
            it.inputs.file(rootProject.file("core/target/debug/libonklav_core.so"))
            it.systemProperty("java.library.path", rootProject.file("core/target/debug").absolutePath)
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            listOf("https.proxyHost", "https.proxyPort", "http.proxyHost", "http.proxyPort", "javax.net.ssl.trustStore").forEach { key ->
                System.getProperty(key)?.let { value -> it.systemProperty(key, value) }
            }
            // File identity is a test input so consecutive previews cannot reuse an old PNG.
            System.getenv("ONKLAV_PREVIEW_RUN")?.let { preview -> it.inputs.file(file(preview));it.outputs.upToDateWhen { false } }
            // Robolectric/Conscrypt loopback TLS fixtures inspect InetAddress on JDK 17.
            it.jvmArgs("--add-opens=java.base/java.net=ALL-UNNAMED")
            it.maxHeapSize = "2g"
            it.maxParallelForks = 1
            // A JNI library cannot be shared by different Robolectric classloaders.
            it.forkEvery = 1
            it.testLogging { events("started", "passed", "skipped", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
        }
    }
    packaging { resources.merges += setOf("META-INF/LICENSE.md", "META-INF/NOTICE.md", "META-INF/LICENSE", "META-INF/NOTICE"); resources.excludes += setOf("META-INF/LICENSE.txt", "META-INF/NOTICE.txt");jniLibs {excludes += setOf("**/.*");useLegacyPackaging=true;keepDebugSymbols += setOf("**/libonklav_core.so", "**/libonklav_speech.so", "**/libsherpa-onnx-c-api.so", "**/libonnxruntime.so")}}
    buildTypes {
        getByName("debug") {applicationIdSuffix=".rewrite";isMinifyEnabled=false;proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"));testProguardFiles("test-proguard-rules.pro")}
        create("diagnostic") {
            initWith(getByName("debug"))
            applicationIdSuffix=".diagnostics"
            versionNameSuffix="-device-checks2"

            signingConfig=signingConfigs.getByName("debug")
            matchingFallbacks+=listOf("debug", "release")
        }
        create("optimized") {
            initWith(getByName("debug"))
            applicationIdSuffix=".rewrite"
            isDebuggable=false
            isMinifyEnabled=true
            isShrinkResources=true
            signingConfig=signingConfigs.getByName("debug")
            matchingFallbacks+=listOf("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "optimized-rules.pro")
        }
    }
    sourceSets.getByName("testDiagnostic").java.srcDir("src/debug/kotlin")
}
dependencies {
    // Binary DOC/XLS reading only; OOXML/ODF use bounded host readers, no office engine.
    implementation("org.apache.poi:poi:5.5.1")
    implementation("org.apache.poi:poi-scratchpad:5.5.1")
    implementation(fileTree("libs") { include("*.jar") })
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")
    implementation("androidx.camera:camera-video:1.5.3")
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    implementation("androidx.media3:media3-transformer:1.9.3")
    implementation("androidx.media3:media3-effect:1.9.3")
    implementation("androidx.media3:media3-exoplayer:1.9.3")
    implementation("androidx.media3:media3-exoplayer-hls:1.9.3")
    implementation("androidx.media3:media3-ui:1.9.3")
    implementation("com.google.crypto.tink:tink-android:1.24.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation("androidx.webkit:webkit:1.13.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.test.espresso:espresso-core:3.6.1")
    testImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    // Match the annotation baseline selected by the Media3/Guava runtime.
    androidTestImplementation("com.google.errorprone:error_prone_annotations:2.28.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
implementation(project(":android:shared"));implementation(project(":android:tools"));implementation(project(":android:games"))}

androidComponents { onVariants(selector().withBuildType("diagnostic")) { variant -> variant.outputs.forEach { it.versionCode.set(79) } } }

// AGP can mark these thin wrappers current while retaining old Kotlin classes.
// Recreate only application class JAR wrappers; keep compilation/dependency caches.
tasks.configureEach {
    if (name.startsWith("bundle") && (name.endsWith("ClassesToCompileJar") || name.endsWith("ClassesToRuntimeJar"))) {
        outputs.upToDateWhen { false }
    }
}
