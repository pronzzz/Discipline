plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
    namespace = "app.discipline"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.discipline"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Personal project: sign release with the debug key so it installs directly.
            signingConfig = signingConfigs.getByName("debug")
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
    }
}

kotlin {
    jvmToolchain(17)
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
  implementation(libs.play.services.code.scanner)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.play.services)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
