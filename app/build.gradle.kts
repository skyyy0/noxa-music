import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// Fallback signing credentials for the repository's own keystore
// (`my-upload-key.jks`). This is a personal, privately hosted app: committing
// the keystore + password is what lets every CI build produce APKs with ONE
// stable signature, so each new version installs as an in-place UPDATE instead
// of requiring an uninstall. Environment variables still take precedence.
val COMMITTED_KEYSTORE_PASSWORD = "noxa-local-signing"

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    // Renamed from com.aistudio.noxa.music: the app now carries its own
    // identity instead of the AI-Studio template package. NOTE: this changes
    // the install identity — the new APK installs as a fresh app.
    applicationId = "com.noxa.music"
    minSdk = 24
    targetSdk = 36
    versionCode = 24
    versionName = "3.2.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Signing configuration.
  //
  // AI Studio ships the release keystore at ${rootDir}/my-upload-key.jks and passes
  // KEYSTORE_PATH / STORE_PASSWORD / KEY_PASSWORD. Codemagic instead exports
  // CM_KEYSTORE_PATH / CM_KEYSTORE_PASSWORD / CM_KEY_ALIAS / CM_KEY_PASSWORD through
  // its `android_signing` integration. Both are supported here.
  //
  // A signing config is only registered when its keystore actually exists, so the
  // project can also be compiled on CI without any keystore (the release APK is then
  // simply left unsigned instead of failing the build).
  signingConfigs {
    val releaseKeystoreFile = file(
      System.getenv("KEYSTORE_PATH")
        ?: System.getenv("CM_KEYSTORE_PATH")
        ?: "${rootDir}/my-upload-key.jks"
    )
    if (releaseKeystoreFile.exists()) {
      create("release") {
        storeFile = releaseKeystoreFile
        storePassword = System.getenv("STORE_PASSWORD")
          ?: System.getenv("CM_KEYSTORE_PASSWORD")
          ?: COMMITTED_KEYSTORE_PASSWORD
        keyAlias = System.getenv("KEY_ALIAS") ?: System.getenv("CM_KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
          ?: System.getenv("CM_KEY_PASSWORD")
          ?: COMMITTED_KEYSTORE_PASSWORD
      }
    }

    val debugKeystoreFile = file("${rootDir}/debug.keystore")
    if (debugKeystoreFile.exists()) {
      create("debugConfig") {
        storeFile = debugKeystoreFile
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      // Konsisten dengan versi 1.15.0: tanpa minifikasi / resource
      // shrinking. Konfigurasi resource (Google/Firebase) tetap utuh dan
      // ukuran APK berada di kisaran 30–50 MB sesuai ekspektasi user.
      isCrunchPngs = true
      isMinifyEnabled = false
      isShrinkResources = false
      signingConfigs.findByName("release")?.let { signingConfig = it }
    }
    debug {
      // Uses the AI Studio debug keystore when present, otherwise falls back to the
      // standard auto-generated Android debug keystore.
      signingConfigs.findByName("debugConfig")?.let { signingConfig = it }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    // NewPipeExtractor uses java.nio.file APIs that need core-library
    // desugaring on minSdk < 33 (per the library's README).
    isCoreLibraryDesugaringEnabled = true
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging {
    resources {
      // protobuf-javalite (stripped copy) and protolite-well-known-types both
      // ship the descriptor .proto resource; the duplicate breaks mergeJavaRes.
      excludes += "google/protobuf/descriptor.proto"
    }
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// ---------------------------------------------------------------------------
// Firestore ↔ protobuf de-duplication.
//
// protolite-well-known-types (Firestore's protobuf runtime) bundles its own
// copy of `com.google.protobuf.DescriptorProtos*` inside the AAR, while
// NewPipeExtractor pulls `com.google.protobuf:protobuf-javalite` from Maven,
// which ships the same classes — dex then fails with "Duplicate class".
//
// Fix: exclude the Maven protobuf-javalite everywhere, and hand the app a
// stripped copy of that same jar (identical runtime, DescriptorProtos
// removed). Firestore's bundled DescriptorProtos remains the single copy;
// NewPipe only needs the runtime classes, which the stripped jar still has.
// ---------------------------------------------------------------------------
abstract class StripDescriptorProtos : DefaultTask() {
    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun strip() {
        val out = ZipOutputStream(outputJar.get().asFile.outputStream().buffered())
        try {
            ZipFile(inputJar.get().asFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory ||
                        entry.name.startsWith("com/google/protobuf/DescriptorProtos")
                    ) {
                        return@forEach
                    }
                    out.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        } finally {
            out.close()
        }
    }
}

val protobufJavaliteOnly: Configuration = configurations.detachedConfiguration(
    dependencies.create("com.google.protobuf:protobuf-javalite:4.35.1")
)

val stripDescriptorProtos = tasks.register<StripDescriptorProtos>("stripDescriptorProtos") {
    inputJar.set(protobufJavaliteOnly.singleFile)
    outputJar.set(layout.buildDirectory.file("intermediates/protobuf-javalite-stripped.jar"))
}

configurations.configureEach {
    // The stripped jar below replaces the Maven artifact everywhere; only the
    // detached resolution above is allowed to see the original.
    if (name != protobufJavaliteOnly.name) {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  implementation(libs.firebase.ai)
  // Firestore is back (v1.10): the unique-username registry claims names in
  // `usernames/{name}` via a transaction. Its transitive
  // protolite-well-known-types collides with NewPipeExtractor's protobuf
  // classes in dex — resolved by the stripDescriptorProtos setup above.
  implementation(files(stripDescriptorProtos))
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  // On-device YouTube audio extraction: full-length streams without any
  // Piped/Invidious instance. License GPL-3.0 — fine for this private app.
  implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.1")
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // "Bagikan QR": encoding the NOXA deep link into a scannable code.
  implementation(libs.zxing)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
