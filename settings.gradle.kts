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

val dbKmpSdkPath = providers.gradleProperty("ogt.dbKmpSdkPath")
    .getOrElse("/Users/romancanoniero/AndroidStudioProjects/db-kmp-sdk")
val dbKmpSdk = file(dbKmpSdkPath)
if (dbKmpSdk.exists()) {
    includeBuild(dbKmpSdk) {
        dependencySubstitution {
            substitute(module("io.github.romancanoniero:db-core"))
                .using(project(":db-core"))
            substitute(module("io.github.romancanoniero:db-database"))
                .using(project(":db-database"))
        }
    }
}
