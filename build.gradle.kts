import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.0" // Update to Kotlin 2.0.0
    kotlin("plugin.serialization") version "2.0.0" // Match Kotlin version
    application // Keep application plugin
    // id("com.bmuschko.docker-remote-api") version "9.4.0" // Ensure Docker plugin is removed/commented
}

group = "com.example.mcpocr"
version = "1.0-SNAPSHOT"

// Define Ktor version
val mcpSdkVersion = "0.4.0" // Re-add SDK version
repositories {
    mavenCentral()
}

dependencies {
    // Kotlin standard library
    implementation(kotlin("stdlib"))

    // Tess4J for OCR
    implementation("net.sourceforge.tess4j:tess4j:5.11.0") // Placeholder version

    // Apache PDFBox for PDF processing
    implementation("org.apache.pdfbox:pdfbox:3.0.2") // Placeholder version

    // Kotlinx Serialization for JSON (MCP communication)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // Placeholder version

    // Logging Facade
    implementation("org.slf4j:slf4j-api:2.0.13") // Placeholder version

    // Logging Implementation
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6") // Placeholder version

    // Add Coroutines dependency (needed for stdio version's handlers)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0") // Use a recent version

    // Add MCP Kotlin SDK dependency
    implementation("io.modelcontextprotocol:kotlin-sdk:$mcpSdkVersion")

    // Testing
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// tasks.withType<KotlinCompile> { // Remove deprecated block
//    kotlinOptions.jvmTarget = "21"
// }

// Use the modern compilerOptions DSL within the kotlin extension
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) // Set JVM target using compilerOptions
    }
}

application { // Keep application plugin for potential direct execution/packaging
    mainClass.set("com.example.mcpocr.MainKt") // Set the main class (used by SDK's Ktor engine)
}

// Ensure native libraries for Tess4J are handled if needed,
// though typically Tesseract needs to be installed system-wide.
// Docker configuration removed temporarily

// Optional: Add buildDockerImage to the default build lifecycle if desired
// tasks.named("build") {
//     finalizedBy(tasks.named("buildDockerImage"))
// }
// though typically Tesseract needs to be installed system-wide.