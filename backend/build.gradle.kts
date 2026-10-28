plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.onlygoodthings.backend.ApplicationKt")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.double.receive)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.datatype.jsr310)

    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.jedis)
    implementation(libs.firebase.admin)
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.12.0")
    implementation("com.drewnoakes:metadata-extractor:2.19.0")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
    testImplementation("io.ktor:ktor-server-test-host:3.1.3")
}

tasks.test {
    useJUnitPlatform { excludeTags("integration","business-integration") }
}

val sourceSets=the<SourceSetContainer>()
tasks.register<Test>("businessIntegrationTest") {
    doNotTrackState("Exercises the current isolated database; a cached result cannot verify external state")
    group="verification"
    description="Verifies company isolation, transactional balances, coupons and payment retries in the isolated QA database."
    testClassesDirs=sourceSets["test"].output.classesDirs
    classpath=sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("business-integration") }
}

tasks.register<Test>("accountErasureIntegrationTest") {
    doNotTrackState("Verifies current database deletion retries and must execute for each invocation")
    group="verification"
    description="Checks durable deletion retries and concurrency against a dedicated local QA database."
    testClassesDirs=sourceSets["test"].output.classesDirs
    classpath=sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.test)
}
