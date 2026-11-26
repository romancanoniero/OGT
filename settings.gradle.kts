pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        maven {
            name = "IyrCommonLibPublic"
            url = uri("https://romancanoniero.github.io/iyrcommonlib-maven")
            content {
                includeModule("io.github.romancanoniero", "iyrcommonlib")
                includeModule("io.github.romancanoniero", "iyrcommonlib-android")
                includeModule("io.github.romancanoniero", "iyrcommonlib-iosarm64")
                includeModule("io.github.romancanoniero", "iyrcommonlib-iossimulatorarm64")
                includeModule("io.github.romancanoniero", "iyrcommonlib-iosx64")
            }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "OnlyGoodThings"

include(":shared")
include(":composeApp")
include(":backend")

// CI/release opts into source substitution explicitly; normal IDE imports use installed artifacts.
val releaseSdkPath = providers.gradleProperty("ogt.dbKmpSdkPath")
    .orElse(providers.environmentVariable("OGT_DB_KMP_SDK_PATH")).orNull
if (!releaseSdkPath.isNullOrBlank()) {
    val releaseSdk = file(releaseSdkPath)
    check(releaseSdk.resolve("settings.gradle.kts").isFile) { "El SDK de la compilación no está disponible." }
    includeBuild(releaseSdk) {
        dependencySubstitution {
            substitute(module("io.github.romancanoniero:db-core")).using(project(":db-core"))
            substitute(module("io.github.romancanoniero:db-database")).using(project(":db-database"))
        }
    }
}
