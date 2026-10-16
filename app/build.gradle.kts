import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.a24solver.wakusei"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.a24solver.wakusei"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8 is deliberately left off. The template default is off, and turning it on is a
            // change to the one artifact users actually install: it can only be validated by
            // running the minified build on a device, which the checks in this repository cannot
            // do. Enable it (optimization.enable = true) together with keep rules for the two
            // fragments, which res/navigation/nav_graph.xml instantiates by name, and smoke-test
            // the release build before shipping. See README §5.5.
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
    testOptions {
        // lets the Robolectric unit tests see the merged resources/manifest
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

// Robolectric used to need a Java 21 launcher here: 4.14.1 shipped an ASM that could not read the
// JDK 25 platform classes the Gradle daemon runs on ("Unsupported class file major version 69").
// 4.17 reads them, so the tests now run on the daemon's own JVM again. If a future downgrade of
// Robolectric brings the error back, restore the launcher:
//
//     tasks.withType<Test>().configureEach {
//         javaLauncher.set(javaToolchains.launcherFor {
//             languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21))
//         })
//     }
// Show the failure and its stack trace in the console, so a red test does not require opening the
// HTML report to find out what happened.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}