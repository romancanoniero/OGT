plugins {
    idea
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

// IDE-only exclusions, retained when Android Studio reimports the Gradle model.
// Module build directories and generated sources remain managed by Android/KMP.
idea {
    module {
        excludeDirs.addAll(
            files(
                ".gradle",
                ".kotlin",
                ".recovery",
                "build",
                "kotlin-js-store",
                "qa/node_modules",
                "qa/artifacts",
                "qa/test-results",
                "qa/playwright-report",
                "qa/__pycache__",
            ),
        )
    }
}
