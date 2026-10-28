plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Version numbering: X.YY.ZZ with zero-padded two-digit YY and ZZ.
// versionCode is derived, never hand-maintained, so it cannot drift or decrease:
//   versionCode = X * 10000 + YY * 100 + ZZ
// 0.01.00 -> 100, 0.04.00 -> 400, 1.00.00 -> 10000.
val appVersionName = "0.04.01"

val appVersionCode = run {
    val parts = appVersionName.split(".")
    require(parts.size == 3) { "versionName must be X.YY.ZZ, was '$appVersionName'" }
    val (major, feature, bugfix) = parts
    require(feature.length == 2 && bugfix.length == 2) {
        "YY and ZZ must be zero-padded to two digits, was '$appVersionName'"
    }
    major.toInt() * 10000 + feature.toInt() * 100 + bugfix.toInt()
}

android {
    namespace = "org.snrkl.nfcbusinesscard"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.snrkl.nfcbusinesscard"
        minSdk = 29
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Built unsigned on purpose. Signing is a separate, interactive step
            // outside Gradle, so the keystore passphrase is never stored where
            // Gradle could read it. Do not add a signingConfig here.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }

    buildFeatures {
        compose = true
        // Enabled for the app module only. The version string is shown in
        // settings > about, and the release process requires it to match the
        // published tag — reading it from BuildConfig means it cannot drift from
        // the value Gradle actually built with.
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
        }
    }

    lint {
        // The build loop treats lint as a gate, so warnings are surfaced but only
        // real errors stop the build.
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = true
        htmlReport = true
        xmlReport = true
        textReport = true
        disable += setOf(
            // Debug-only distribution: there is no Play listing to keep current.
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
        )
    }
}

/**
 * Generates the Card Face placeholder PNGs and the geometry constants the layout
 * reads, from a single spec in buildSrc.
 *
 * Wiring the outputs in as source directories via the task provider gives Gradle
 * the task dependency implicitly, so the assets are always regenerated before
 * anything compiles against them or packages them.
 */
val generateCardFaceAssets = tasks.register<GenerateCardFaceAssets>("generateCardFaceAssets") {
    group = "build"
    description = "Generates the pro-forma Card Face placeholders and their geometry constants."
    resOutputDir.set(layout.buildDirectory.dir("generated/cardface/res"))
    kotlinOutputDir.set(layout.buildDirectory.dir("generated/cardface/kotlin"))
}

android.sourceSets.getByName("main") {
    res.srcDir(generateCardFaceAssets.map { it.resOutputDir })
    kotlin.srcDir(generateCardFaceAssets.map { it.kotlinOutputDir })
}

// Belt and braces. The task providers above usually carry the dependency, but
// AGP's resource merge does not always infer it from a generated srcDir — which
// showed up as an intermittent "resource not found" for a generated drawable on
// a clean build and nowhere else. Hanging it off preBuild makes the ordering
// explicit.
tasks.named("preBuild") {
    dependsOn(generateCardFaceAssets)
}

/**
 * Exports a placeholder into assets/ on demand.
 *
 * assets/ is version controlled, so the placeholders are not written there on
 * every build — that would put binary churn in every commit. This task exists so
 * anyone can produce a reference copy to export real artwork against.
 */
tasks.register<Copy>("exportCardFacePlaceholder") {
    group = "build"
    description = "Copies the generated Card Face placeholders into assets/ for reference."
    from(generateCardFaceAssets.map { it.resOutputDir.get().asFile.resolve("drawable-nodpi") }) {
        include("card_face_placeholder_*.png")
    }
    into(rootProject.layout.projectDirectory.dir("assets/card-face"))
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
