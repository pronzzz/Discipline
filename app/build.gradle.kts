import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

/**
 * Release signing. Values come from environment variables (CI) or an untracked
 * keystore.properties file (local builds). Without them, release builds fall back to the
 * debug key, which is what v1.0.0 shipped with. Updates must keep using the same key or
 * Android will refuse to install them over the existing app.
 */
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signing(env: String, prop: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)
val releaseStore = signing("DISCIPLINE_KEYSTORE", "storeFile")

android {
    namespace = "app.discipline"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.discipline"
        minSdk = 28
        targetSdk = 36
        // major * 10000 + minor * 100 + patch
        versionCode = 10100
        versionName = "1.1.0"
        // ML Kit's native libraries are large; real phones are ARM, so x86 builds are left out.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = rootProject.file(releaseStore)
                storePassword = signing("DISCIPLINE_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signing("DISCIPLINE_KEY_ALIAS", "keyAlias")
                keyPassword = signing("DISCIPLINE_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
      // Compress native libraries in the APK: a much smaller download for sideloaded updates.
      jniLibs { useLegacyPackaging = true }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        optIn.add("androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons)
  debugImplementation(libs.androidx.compose.ui.tooling)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.health.connect)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.camera.mlkit)
  implementation(libs.mlkit.barcode)
  implementation(libs.mlkit.labeling.custom)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.play.services)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
