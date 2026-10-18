import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The release key lives outside this repository, which is public: the file says where it is and how to open
// it, and neither it nor the key is ever committed. Without it, `assembleRelease` still builds — unsigned — so
// a fresh clone is not broken, it just cannot produce an installable APK.
val signing = File(System.getProperty("user.home"), ".android/tetra-release.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

android {
    namespace = "com.kalotrapezis.drive"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kalotrapezis.drive"
        minSdk = 30
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 12
        versionName = "0.6.0-beta.1"
    }
    signingConfigs {
        signing?.let { properties ->
            create("release") {
                storeFile = file(properties.getProperty("storeFile"))
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signing?.let { signingConfig = signingConfigs.getByName("release") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { compose = true }
    // One APK per processor. OpenCV, ML Kit, TensorFlow and MapLibre each ship a native library for four
    // architectures, and together they were 346 MB of a 423 MB APK — two thirds of it for emulators nobody
    // installs this on. Split, an arm64 phone downloads only its own.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }
}

dependencies {
    val cameraX = "1.6.2"
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.google.zxing:core:3.5.3") // drawing a QR code; ML Kit only reads them
    implementation("com.mohamedrejeb.richeditor:richeditor-compose:1.2.0") // Writer: bold, lists, headings as you type (Apache 2.0)
    implementation("org.opencv:opencv:4.12.0")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
    implementation("org.maplibre.gl:android-sdk-opengl:13.6.1")
    implementation("org.dmfs:lib-recur:0.17.1") // Calendar: iCalendar RRULE repeats (Apache 2.0)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303") // real org.json for JVM tests (Android stubs it)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

// The tetra-calendar skill, zipped into the app at build time from skills/tetra-calendar (its one source), so Calendar ›
// Share with Claude always hands over the skill that matches this app's files (asked 18 Oct).
abstract class SkillZip : DefaultTask() {
    @get:InputDirectory abstract val skill: DirectoryProperty
    @get:OutputDirectory abstract val out: DirectoryProperty
    @TaskAction fun zip() {
        val dir = out.get().asFile.apply { deleteRecursively(); mkdirs() }
        ZipOutputStream(File(dir, "tetra-calendar.zip").outputStream()).use { z ->
            skill.get().asFile.listFiles()!!.filter { it.isFile && !it.name.startsWith(".") }.sortedBy { it.name }.forEach { f ->
                z.putNextEntry(ZipEntry("tetra-calendar/${f.name}")); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
    }
}
val skillZip = tasks.register<SkillZip>("skillZip") {
    skill.set(rootProject.layout.projectDirectory.dir("skills/tetra-calendar"))
    out.set(layout.buildDirectory.dir("generated/skillAssets"))
}
androidComponents { onVariants { it.sources.assets?.addGeneratedSourceDirectory(skillZip, SkillZip::out) } }
