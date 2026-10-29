import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

/**
 * UI compartida Android + iOS: pantallas en commonMain (Compose Multiplatform).
 */
kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    androidTarget {
        // Include common assertions on devices without disabling the default
        // hierarchy that connects iosMain/iosTest to their native targets.
        instrumentedTestVariant { sourceSetTree.set(KotlinSourceSetTree.test) }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.animation)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtime)
            implementation("androidx.fragment:fragment-ktx:1.8.6")
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.auth)
            implementation(libs.firebase.messaging)
            implementation("androidx.core:core-ktx:1.15.0")
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play)
            implementation(libs.googleid)
            implementation(libs.androidx.biometric)
            implementation(libs.kotlinx.coroutines.play)
            implementation(libs.play.services.location)
            implementation("com.google.android.gms:play-services-auth-api-phone:18.1.0")
            implementation("com.android.installreferrer:installreferrer:2.2")
            implementation("androidx.media3:media3-transformer:1.9.3")
            implementation("androidx.media3:media3-effect:1.9.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidInstrumentedTest {
            dependencies { implementation("androidx.test:runner:1.7.0") }
        }
    }
}

val ogtReleaseProps = Properties().apply {
    val local = rootProject.file("key.properties")
    val devMac = File("/Volumes/DEV_MAC/secrets/OGT/ogt-release.properties")
    val devMacLegacy = File("/Volumes/DEV_MAC/OGT/secrets/ogt-release.properties")
    val home = File(System.getProperty("user.home"), ".android/ogt-release.properties")
    when {
        local.exists() -> local.inputStream().use { load(it) }
        devMac.exists() -> devMac.inputStream().use { load(it) }
        devMacLegacy.exists() -> devMacLegacy.inputStream().use { load(it) }
        home.exists() -> home.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.onlygoodthings.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.onlygoodthings.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.0"
        testInstrumentationRunner = "com.onlygoodthings.app.qa.QaTestRunner"
        manifestPlaceholders["ogtAppLabel"] = "Only Good Things"
        manifestPlaceholders["ogtFcmAutoInit"] = true
    }
    signingConfigs {
        if (ogtReleaseProps.isNotEmpty()) {
            create("release") {
                val canonical = File("/Volumes/DEV_MAC/secrets/OGT/ogt-release.jks")
                val legacy = File("/Volumes/DEV_MAC/OGT/secrets/ogt-release.jks")
                storeFile = when {
                    canonical.exists() -> canonical
                    legacy.exists() -> legacy
                    else -> file(ogtReleaseProps.getProperty("storeFile"))
                }
                storePassword = ogtReleaseProps.getProperty("storePassword")
                keyAlias = ogtReleaseProps.getProperty("keyAlias")
                keyPassword = ogtReleaseProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if(providers.gradleProperty("ogt.instrumentedQa").orNull=="true") {
                applicationIdSuffix=".qa"
                manifestPlaceholders["ogtAppLabel"]="Only Good Things QA"
                manifestPlaceholders["ogtFcmAutoInit"]=false
            }
        }
        getByName("release") {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// These exact assertions also run in commonTest on iOS and on Android devices.
// The JVM's android.jar cannot decode bitmaps or create an Application context.
tasks.withType<Test>().configureEach {
    filter {
        excludeTestsMatching("com.onlygoodthings.app.platform.StoryBitmapTest")
        excludeTestsMatching("com.onlygoodthings.app.data.SessionProfileTest")
        excludeTestsMatching("com.onlygoodthings.app.data.StoryMediaCacheTest")
    }
}
tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
    doFirst { check(providers.gradleProperty("ogt.instrumentedQa").orNull=="true") { "Device tests require the isolated .qa package; use -Pogt.instrumentedQa=true -I qa/android-instrumented.init.gradle" } }
}

compose {
    resources {
        publicResClass = true
        packageOfResClass = "com.onlygoodthings.app.resources"
        generateResClass = auto
    }
}
