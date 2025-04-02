# MCP OCR Server Project Context

**Status:** Initial implementation complete.

**Goal:** Create a Kotlin Multiplatform MCP server for OCR on Linux.

**Approach:**
*   Language: Kotlin (JVM target)
*   OCR: Tess4J (Java wrapper for Tesseract)
*   PDF Handling: Apache PDFBox
*   Build System: Gradle
*   Deployment: Docker

**Key Files:**
*   `build.gradle.kts`: Defines project structure, dependencies (Tess4J, PDFBox, Kotlinx Serialization, Logging), application plugin, and a `buildDockerImage` task using `com.bmuschko.docker-remote-api`.
*   `src/main/kotlin/com/example/mcpocr/Main.kt`: Contains the main MCP server loop, tool dispatching logic, `OcrService` (using Tess4J), and `PdfProcessor` (using PDFBox). Implements `image_to_text` and `pdf_to_text` tools.
*   `Dockerfile`: Defines the image based on `eclipse-temurin:17-jre-jammy`, installs `tesseract-ocr` and `tesseract-ocr-eng`, copies the application, builds it using `./gradlew build`, and sets the entry point to run the JAR.
*   `PLAN.llm.md`: Detailed plan document created during the architect phase.

**Build Status:** `./gradlew build` completes successfully after resolving several compilation and configuration issues.

**Next Steps:**
*   Testing the implementation (unit and integration).
*   Running the server via Docker (`./gradlew buildDockerImage` then `docker run`).
*   Refining error handling, configuration, and logging.