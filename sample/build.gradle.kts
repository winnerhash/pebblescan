plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "io.github.g00fy2.quickiesample"
  defaultConfig {
    applicationId = providers.gradleProperty("sampleApplicationId").getOrElse("io.github.g00fy2.quickiesample.local")
    versionCode = 12
    versionName = "1.11"
  }
  buildTypes {
    getByName("release") {
      isShrinkResources = true
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  splits {
    abi {
      isEnable = true
      reset()
      include("x86", "armeabi-v7a", "arm64-v8a", "x86_64")
      isUniversalApk = true
    }
  }
  flavorDimensions += "mlkit"
  productFlavors {
    create("bundled").dimension = "mlkit"
    create("unbundled").dimension = "mlkit"
  }
  buildFeatures {
    buildConfig = true
    viewBinding = true
  }
  testOptions {
    unitTests.isIncludeAndroidResources = true
  }
  lint {
    abortOnError = true
    warningsAsErrors = true
    checkDependencies = true
    disable.addAll(
      listOf(
        "RtlEnabled",
        "GradleDependency",
        "AndroidGradlePluginVersion",
        "OldTargetApi",
        "NewerVersionAvailable",
      )
    )
  }
}

dependencies {
  implementation(project(":quickie"))

  implementation(libs.google.materialDesign)
  testImplementation(libs.test.junit4)
  testImplementation(libs.test.robolectric)
  testImplementation(libs.androidx.cameraPreview)
  testImplementation(libs.mlkit.barcodeScanning)
}
