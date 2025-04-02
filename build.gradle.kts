import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.23" // Use a recent Kotlin version
    kotlin("plugin.serialization") version "1.9.23"
    application
    id("com.bmuschko.docker-remote-api") version "9.4.0" // Docker plugin
}

group = "com.example.mcpocr"
version = "1.0-SNAPSHOT"

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

    // Testing
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "21" // Match Java target (detected as 21)
}

application {
    mainClass.set("com.example.mcpocr.MainKt") // Set the main class
}

// Ensure native libraries for Tess4J are handled if needed,
// though typically Tesseract needs to be installed system-wide.
// Docker configuration
// Import moved below plugins block to ensure plugin is applied first

val dockerImageName = "mcp-ocr-server" // Define image name
val dockerImageTag = project.version.toString() // Use project version as tag
// Removed import for DockerBuildImage

// Configure Docker task without explicit type
tasks.register("buildDockerImage") {
    group = "docker"
    description = "Builds the Docker image for the MCP OCR server."
    dependsOn(tasks.named("build")) // Ensure the project is built first

    // Access properties dynamically or assume they are available on the task object
    val task = this as? com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
    if (task != null) {
        task.inputDir.set(project.layout.projectDirectory) // Context is the project root
        task.images.add("$dockerImageName:$dockerImageTag")
        task.images.add("$dockerImageName:latest") // Also tag as latest
    } else {
        // Fallback or error if type casting fails - though this shouldn't happen if plugin is applied
        logger.warn("Could not cast buildDockerImage task to DockerBuildImage type for configuration.")
    }
}

// Optional: Add buildDockerImage to the default build lifecycle if desired
// tasks.named("build") {
//     finalizedBy(tasks.named("buildDockerImage"))
// }
// though typically Tesseract needs to be installed system-wide.