package com.example.mcpocr

// Core Kotlin/Java
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

// Logging
import org.slf4j.LoggerFactory

// Serialization
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.SerializationException // Import for specific exception handling

// Coroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Ktor (Needed for Application, embeddedServer, Netty)
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

// MCP Kotlin SDK
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import io.modelcontextprotocol.kotlin.sdk.datatypes.* // Includes Implementation, ServerCapabilities, ToolDefinition, ToolExecutionRequest, ToolResult, ToolError

// Tesseract/PDFBox
import net.sourceforge.tess4j.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer


// --- Argument & Result Data Classes ---

@Serializable
data class ImageToTextArgs(
    val image_path: String,
    val language: String = "eng"
)

@Serializable
data class PdfToTextArgs(
    val pdf_path: String,
    val language: String = "eng"
)

// Define a simple data class for the successful result payload
@Serializable
data class OcrResult(val text: String)

// --- Main Application & Ktor Setup ---

private val logger = LoggerFactory.getLogger("McpOcrServer")
// Use the Json instance provided by the SDK for consistency
private val json = Json { ignoreUnknownKeys = true; prettyPrint = true } // Or potentially MCP.JSON

fun main() {
    val port = 8080 // Consider making configurable
    logger.info("Starting Ktor MCP OCR Server with SDK on port {}...", port)
    // SDK handles server setup internally when using Ktor integration
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    // Initialize Services (Consider dependency injection for real apps)
    val tessDataPath = environment.config.propertyOrNull("ocr.tessdata_prefix")?.getString()
        ?: System.getenv("TESSDATA_PREFIX")
        ?: "/usr/share/tesseract-ocr/4.00/tessdata" // Default for Debian/Ubuntu Tesseract 4
    logger.info("Using TESSDATA_PREFIX: {}", tessDataPath)
    val ocrService = OcrService(tessDataPath)
    val pdfProcessor = PdfProcessor()

    // Configure MCP Server using SDK's Ktor extension
    mcp { // Installs required Ktor plugins (SSE, ContentNegotiation) and sets up routing
        Server(
            serverInfo = Implementation(name = "mcp-ocr-server", version = "1.0.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = null) // Indicate tool capability
                )
            )
        ).apply {
            // Define JSON Schemas (as strings)
            val imageToTextInputSchema = """
                {
                  "type": "object",
                  "properties": {
                    "image_path": { "type": "string", "description": "Absolute path to the input image file." },
                    "language": { "type": "string", "description": "Tesseract language code(s). Defaults to 'eng'.", "default": "eng" }
                  },
                  "required": ["image_path"]
                }
            """.trimIndent()
            val pdfToTextInputSchema = """
                {
                  "type": "object",
                  "properties": {
                    "pdf_path": { "type": "string", "description": "Absolute path to the input PDF file." },
                    "language": { "type": "string", "description": "Tesseract language code(s). Defaults to 'eng'.", "default": "eng" }
                  },
                  "required": ["pdf_path"]
                }
            """.trimIndent()
             val ocrOutputSchema = """
                {
                  "type": "object",
                  "properties": {
                    "text": { "type": "string", "description": "The extracted text content." }
                  },
                   "required": ["text"]
                }
            """.trimIndent()

            // Add image_to_text tool
            addTool(ToolDefinition(
                name = "image_to_text",
                description = "Extracts text from a given image file.",
                inputSchema = Json.parseToJsonElement(imageToTextInputSchema),
                outputSchema = Json.parseToJsonElement(ocrOutputSchema),
                handler = { request -> handleImageToText(request, ocrService) } // Pass needed service
            ))

            // Add pdf_to_text tool
            addTool(ToolDefinition(
                name = "pdf_to_text",
                description = "Extracts text from all pages of a given PDF file.",
                inputSchema = Json.parseToJsonElement(pdfToTextInputSchema),
                outputSchema = Json.parseToJsonElement(ocrOutputSchema),
                handler = { request -> handlePdfToText(request, ocrService, pdfProcessor) } // Pass needed services
            ))
        }
    }
}

// --- Tool Handler Functions ---

private suspend fun handleImageToText(
    request: ToolExecutionRequest,
    ocrService: OcrService
): ToolResult {
    return try {
        val args = request.argumentsAs<ImageToTextArgs>() // Use SDK extension function
        logger.info("Executing image_to_text for path: {}", args.image_path)

        val imageFile = File(args.image_path)
        // Use Java NIO Files.exists for potentially better compatibility if basic File.exists fails
        // if (!Files.exists(imageFile.toPath())) {
        if (!imageFile.exists()) { // Revert to standard check first
             throw IllegalArgumentException("Image file not found: ${args.image_path}")
        }

        val image = withContext(Dispatchers.IO) {
            ImageIO.read(imageFile) // Use standard ImageIO.read
                ?: throw IllegalArgumentException("Could not read image file: ${args.image_path}")
        }

        // Run OCR in a background thread pool suitable for CPU-bound tasks
        withContext(Dispatchers.Default) {
            ocrService.performOcr(image, args.language)
        }.fold(
            onSuccess = { text -> ToolResult(result = json.encodeToJsonElement(OcrResult(text))) },
            onFailure = { error -> ToolResult(error = ToolError(message = "OCR failed: ${error.message}")) }
        )
    } catch (e: SerializationException) {
        logger.error("Invalid arguments for image_to_text: {}", request.arguments, e)
        ToolResult(error = ToolError(message = "Invalid arguments: ${e.message}"))
    } catch (e: IllegalArgumentException) {
        logger.error("Invalid argument for image_to_text: {}", e.message)
        ToolResult(error = ToolError(message = "Invalid argument: ${e.message}"))
    } catch (e: Exception) {
        logger.error("Unexpected error in image_to_text", e)
        ToolResult(error = ToolError(message = "Internal server error: ${e.message ?: e.javaClass.simpleName}"))
    }
}

private suspend fun handlePdfToText(
    request: ToolExecutionRequest,
    ocrService: OcrService,
    pdfProcessor: PdfProcessor
): ToolResult {
    return try {
        val args = request.argumentsAs<PdfToTextArgs>() // Use SDK extension function
        logger.info("Processing pdf_to_text for path: {}", args.pdf_path)

        // Extract images (IO-bound)
        val imagesResult = withContext(Dispatchers.IO) {
            pdfProcessor.extractImagesFromPdf(args.pdf_path)
        }

        imagesResult.fold(
            onSuccess = { images ->
                // Perform OCR on each image (CPU-bound) and combine results
                val pageResults = images.mapIndexed { index, image ->
                     logger.debug("Performing OCR on page {} of {}", index + 1, images.size)
                     withContext(Dispatchers.Default) {
                         ocrService.performOcr(image, args.language)
                     } // Result<String>
                 }

                // Combine successful results, report first error if any page failed
                val errors = pageResults.mapNotNull { it.exceptionOrNull() }
                if (errors.isNotEmpty()) {
                     logger.error("OCR failed on one or more pages for PDF: {}", args.pdf_path)
                     ToolResult(error = ToolError(message = "OCR failed on page(s): ${errors.first().message}"))
                } else {
                    val combinedText = pageResults.mapNotNull { it.getOrNull() }.joinToString("\n\n--- Page Break ---\n\n")
                    ToolResult(result = json.encodeToJsonElement(OcrResult(combinedText)))
                }
            },
            onFailure = { error ->
                ToolResult(error = ToolError(message = "PDF processing failed: ${error.message}"))
            }
        )
    } catch (e: SerializationException) {
        logger.error("Invalid arguments for pdf_to_text: {}", request.arguments, e)
        ToolResult(error = ToolError(message = "Invalid arguments: ${e.message}"))
    } catch (e: IllegalArgumentException) {
        logger.error("Invalid argument for pdf_to_text: {}", e.message)
        ToolResult(error = ToolError(message = "Invalid argument: ${e.message}"))
    } catch (e: Exception) {
        logger.error("Unexpected error in pdf_to_text", e)
        ToolResult(error = ToolError(message = "Internal server error: ${e.message ?: e.javaClass.simpleName}"))
    }
}


// --- OCR and PDF Service Classes ---

class OcrService(tessDataPath: String) {
    private val tesseract: ITesseract = Tesseract().apply {
        setDatapath(tessDataPath)
        logger.info("Tesseract instance initialized with data path: {}", tessDataPath)
    }

    // Note: Tesseract instance might not be thread-safe depending on version/usage.
    // Consider synchronization or creating instances per request if needed.
    fun performOcr(image: BufferedImage, language: String = "eng"): Result<String> {
        logger.debug("Performing OCR with language: {}", language)
        return runCatching {
            tesseract.setLanguage(language)
            tesseract.doOCR(image)
        }.onSuccess { text ->
             logger.debug("OCR successful, text length: {}", text.length)
        }.onFailure { exception ->
            logger.error("Tesseract OCR failed for language {}", language, exception)
        }
    }
}

class PdfProcessor {
    private val renderDpi = 300f // Consider making configurable

    fun extractImagesFromPdf(pdfPath: String): Result<List<BufferedImage>> {
        logger.debug("Extracting images from PDF: {}", pdfPath)
        val pdfFile = File(pdfPath)
        if (!pdfFile.exists()) { // Standard File.exists()
             logger.error("PDF file not found: {}", pdfPath)
             return Result.failure(IllegalArgumentException("PDF file not found: $pdfPath"))
        }

        return runCatching {
            Loader.loadPDF(pdfFile).use { document: PDDocument ->
                if (document.isEncrypted) {
                    logger.warn("PDF is encrypted, attempting to process anyway: {}", pdfPath)
                    // Consider adding decryption logic if needed: document.decrypt("")
                }
                val renderer = PDFRenderer(document)
                logger.debug("PDF has {} pages.", document.numberOfPages)
                List(document.numberOfPages) { pageIndex ->
                    logger.debug("Rendering page {} with DPI {}", pageIndex + 1, renderDpi)
                    renderer.renderImageWithDPI(pageIndex, renderDpi)
                }
            }
        }.onSuccess { imageList: List<BufferedImage> ->
             logger.info("Successfully extracted {} images from PDF: {}", imageList.size, pdfPath)
        }.onFailure { exception: Throwable ->
            logger.error("PDF processing failed for path: {}", pdfPath, exception)
        }
    }
}