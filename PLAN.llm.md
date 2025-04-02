# Detailed Plan: Kotlin OCR MCP Server (using Tess4J)

**1. Goal:**
   Create a Kotlin Multiplatform MCP server providing Optical Character Recognition (OCR) capabilities. The server will run on Linux (targeting JVM) and offer tools to extract text from images and PDF documents.

**2. Core Technology:**
   *   **Language:** Kotlin Multiplatform (targeting JVM for the server).
   *   **OCR Engine:** Tesseract OCR.
   *   **Java Wrapper:** Tess4J (to interact with Tesseract from Kotlin/JVM).
   *   **PDF Processing:** Apache PDFBox (to extract images from PDF pages).

**3. Project Setup & Structure:**
   *   Initialize a Kotlin Multiplatform project using Gradle.
   *   Configure the `build.gradle.kts` file:
        *   Target the JVM platform.
        *   Add necessary Kotlin standard library dependencies.
        *   Add Tess4J dependency (e.g., `net.sourceforge.tess4j:tess4j:<latest_version>`).
        *   Add Apache PDFBox dependency (e.g., `org.apache.pdfbox:pdfbox:<latest_version>`).
        *   Add a logging framework (e.g., SLF4j API + Logback implementation).
   *   Primary logic will reside in the `jvmMain` source set.
   *   Establish basic MCP server structure (handling stdio communication, message parsing/serialization).

**4. System Dependencies (Linux Environment):**
   *   **Java Runtime Environment (JRE):** Required to run the Kotlin/JVM server.
   *   **Tesseract OCR Engine:** The native library must be installed (e.g., `sudo apt install tesseract-ocr` on Debian/Ubuntu).
   *   **Tesseract Language Data:** Install necessary language packs (e.g., `sudo apt install tesseract-ocr-eng` for English). The path to these data files (`tessdata`) will be needed by Tess4J.

**5. MCP Server Definition:**
   *   **Name:** `mcp-ocr`
   *   **Description:** Provides tools for extracting text from images and PDFs using Tesseract OCR.

**6. MCP Tool Definitions:**

   *   **`image_to_text`**
        *   **Description:** Extracts text from a given image file.
        *   **Input Schema:**
            ```json
            {
              "type": "object",
              "properties": {
                "image_path": {
                  "type": "string",
                  "description": "Absolute path to the input image file."
                },
                "language": {
                  "type": "string",
                  "description": "Tesseract language code(s) (e.g., 'eng', 'eng+fra'). Defaults to 'eng'.",
                  "default": "eng"
                }
              },
              "required": ["image_path"]
            }
            ```
        *   **Output Schema (Success):** `{"text": "Extracted text content..."}`
        *   **Output Schema (Error):** `{"error": "Description of the error..."}`

   *   **`pdf_to_text`**
        *   **Description:** Extracts text from all pages of a given PDF file.
        *   **Input Schema:**
            ```json
            {
              "type": "object",
              "properties": {
                "pdf_path": {
                  "type": "string",
                  "description": "Absolute path to the input PDF file."
                },
                "language": {
                  "type": "string",
                  "description": "Tesseract language code(s) (e.g., 'eng', 'fra'). Defaults to 'eng'.",
                  "default": "eng"
                }
              },
              "required": ["pdf_path"]
            }
            ```
        *   **Output Schema (Success):** `{"text": "Extracted text content from all pages..."}`
        *   **Output Schema (Error):** `{"error": "Description of the error..."}`

**7. Core Logic Implementation (`jvmMain`):**

   *   **`OcrService` Class:**
        *   Manages interaction with Tess4J.
        *   Holds a configured `Tesseract` instance (from Tess4J).
        *   Requires the path to the `tessdata` directory during initialization.
        *   Method: `fun performOcr(image: BufferedImage, language: String): Result<String>` - Takes a `BufferedImage` and language, performs OCR, returns `Result` containing text or exception.
   *   **`PdfProcessor` Class:**
        *   Manages interaction with Apache PDFBox.
        *   Method: `fun extractImagesFromPdf(pdfPath: String): Result<List<BufferedImage>>` - Loads PDF, renders each page to a `BufferedImage`, returns `Result` with list or exception.
   *   **Main Server Logic / Tool Handlers:**
        *   Initialize `OcrService` (reading `tessdata` path from config/env).
        *   Initialize `PdfProcessor`.
        *   On `image_to_text` request:
            *   Validate `image_path`.
            *   Load image file into `BufferedImage`.
            *   Call `ocrService.performOcr()`.
            *   Format and return response.
        *   On `pdf_to_text` request:
            *   Validate `pdf_path`.
            *   Call `pdfProcessor.extractImagesFromPdf()`.
            *   If successful, iterate through `BufferedImage` list:
                *   Call `ocrService.performOcr()` for each image.
                *   Collect results.
            *   Concatenate text from all pages.
            *   Format and return response.
        *   Implement robust error handling around file access, PDF processing, and OCR operations.

**8. Configuration:**
   *   Provide a mechanism to configure the `tessdata` path (e.g., via an environment variable `TESSDATA_PREFIX`).
   *   Allow overriding the default language via tool arguments.

**9. Logging:**
   *   Integrate SLF4j + Logback for detailed logging of server startup, tool requests, OCR operations, and errors.

**10. Packaging & Deployment:**
    *   **Recommendation:** Use Docker. Create a `Dockerfile` that:
        *   Starts from a base Linux image with a JRE.
        *   Installs `tesseract-ocr` and required language packs (`tesseract-ocr-eng`, etc.).
        *   Copies the built Kotlin application JAR.
        *   Sets the `TESSDATA_PREFIX` environment variable correctly.
        *   Defines the entry point to run the Kotlin MCP server JAR.
    *   Alternatively, package as a fat JAR, but system dependencies must be managed manually on the host.

**11. Testing:**
    *   **Unit Tests:** Mock `Tesseract` and PDFBox interactions to test service logic and tool handlers without needing native dependencies.
    *   **Integration Tests:** Create tests that run within a Docker container built using the project's `Dockerfile`. These tests will invoke the actual tools with sample image/PDF files and verify the output against the real Tesseract engine.

**12. Conceptual Flow Diagram:**

```mermaid
graph TD
    A[MCP Client Request (image_to_text)] --> B(Kotlin MCP Server);
    B --> C{Input: Image Path/Bytes};
    C --> D[Tess4J API Call (doOCR)];
    D -- Text --> E(Format MCP Response);
    E --> F[MCP Client];
    D -- Error --> E;

    G[MCP Client Request (pdf_to_text)] --> B;
    B --> H{Input: PDF Path/Bytes};
    H --> I[PDF Library (e.g., PDFBox)];
    I -- Page Image --> D;
    I -- Error --> E;


    subgraph Kotlin/JVM Process
        B;
        D;
        I;
    end

    subgraph Linux Environment Dependencies
        J(Native Tesseract Lib);
        K(TessData Language Files);
        D --> J;
        D --> K;
    end