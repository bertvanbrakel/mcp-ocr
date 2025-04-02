package com.example.mcpocr

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import net.sourceforge.tess4j.*
import org.apache.pdfbox.pdmodel.PDDocument // Keep existing import
import org.apache.pdfbox.Loader // Import Loader for static load method
import org.apache.pdfbox.rendering.PDFRenderer
import org.slf4j.LoggerFactory

// --- Data Classes for MCP Communication ---

@Serializable
data class McpRequest(
    val id: String,
    val tool_name: String,
    val arguments: JsonElement // Keep arguments flexible initially
)

@Serializable
data class McpResponse(
    val id: String,
    val result: JsonElement? = null,
    val error: String? = null
)

// --- Argument Data Classes ---

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

@Serializable
data class ToolResult(val text: String)

@Serializable
data class ErrorResult(val message: String)

// --- Main Application ---

private val logger = LoggerFactory.getLogger("McpOcrServer")
private val json = Json { ignoreUnknownKeys = true; prettyPrint = true } // Lenient JSON parsing

fun main() {
    logger.info("MCP OCR Server starting...")

    // Initialize Services
    val tessDataPath = System.getenv("TESSDATA_PREFIX") ?: "/usr/share/tesseract-ocr/4.00/tessdata" // Sensible default for Debian/Ubuntu
    logger.info("Using TESSDATA_PREFIX: {}", tessDataPath)
    val ocrService = OcrService(tessDataPath)
    val pdfProcessor = PdfProcessor()

    try {
        while (true) {
            val line = readlnOrNull() ?: break // Read request line from stdin
            logger.debug("Received raw request: {}", line)

            try {
                val request = json.decodeFromString<McpRequest>(line)
                logger.info("Processing request id: {}, tool: {}", request.id, request.tool_name)

                val response = processRequest(request, ocrService, pdfProcessor)

                val responseJson = json.encodeToString(response)
                println(responseJson) // Write response JSON to stdout
                logger.debug("Sent response: {}", responseJson)

            } catch (e: SerializationException) {
                logger.error("Failed to parse request JSON: {}", line, e)
                // Respond with a generic error if possible, though requires request ID
            } catch (e: Exception) {
                logger.error("Unexpected error processing request: {}", line, e)
                // Respond with a generic error if possible
            }
        }
    } catch (e: Exception) {
        logger.error("Critical error in main loop", e)
    } finally {
        logger.info("MCP OCR Server shutting down.")
    }
}

fun processRequest(request: McpRequest, ocrService: OcrService, pdfProcessor: PdfProcessor): McpResponse {
    return try {
        when (request.tool_name) {
            "image_to_text" -> {
                val args = json.decodeFromJsonElement<ImageToTextArgs>(request.arguments)
                logger.info("Processing image_to_text for path: {}", args.image_path)
                val imageFile = File(args.image_path)
                if (!imageFile.exists()) throw IllegalArgumentException("Image file not found: ${args.image_path}")
                val image = ImageIO.read(imageFile) // Explicitly read from File
                    ?: throw IllegalArgumentException("Could not read image file: ${args.image_path}")

                ocrService.performOcr(image, args.language).fold(
                    onSuccess = { text ->
                        McpResponse(id = request.id, result = json.encodeToJsonElement(ToolResult(text))) // Use named arg
                    },
                    onFailure = { error ->
                        McpResponse(id = request.id, error = "OCR failed: ${error.message}") // Use named arg
                    }
                )
            }
            "pdf_to_text" -> {
                val args = json.decodeFromJsonElement<PdfToTextArgs>(request.arguments)
                logger.info("Processing pdf_to_text for path: {}", args.pdf_path)
                pdfProcessor.extractImagesFromPdf(args.pdf_path).fold(
                    onSuccess = { images ->
                        val combinedText = images.mapIndexedNotNull { index, image ->
                            logger.debug("Performing OCR on page {} of {}", index + 1, images.size) // Corrected logging index
                            ocrService.performOcr(image, args.language).getOrNull() // Get text or null on failure
                        }.joinToString("\n\n--- Page Break ---\n\n") // Add separator between pages
                        McpResponse(id = request.id, result = json.encodeToJsonElement(ToolResult(combinedText))) // Use named arg
                    },
                   onFailure = { pdfError -> // Explicitly name exception
                        McpResponse(id = request.id, error = "PDF processing failed: ${pdfError.message}") // Use named arg
                    }
                )
            }
            else -> McpResponse(id = request.id, error = "Unknown tool: ${request.tool_name}") // Use named arg
        }
    } catch (e: SerializationException) {
        logger.error("Failed to parse arguments for tool {}: {}", request.tool_name, request.arguments, e)
        McpResponse(id = request.id, error = "Invalid arguments for tool ${request.tool_name}: ${e.message}") // Use named arg
    } catch (e: IllegalArgumentException) {
        logger.error("Invalid argument for tool {}: {}", request.tool_name, e.message)
        McpResponse(id = request.id, error = "Invalid argument: ${e.message}") // Use named arg
    } catch (e: Exception) {
        logger.error("Unexpected error processing tool {}: {}", request.tool_name, e.message, e)
        McpResponse(id = request.id, error = "Internal server error: ${e.message}") // Use named arg
    }
}

// --- Placeholder Service Classes (Implement according to PLAN.llm.md) ---

class OcrService(tessDataPath: String) {
    private val tesseract: ITesseract = Tesseract().apply {
        setDatapath(tessDataPath)
        // Consider adding more configurations if needed (e.g., page seg mode)
        logger.info("Tesseract instance initialized with data path: {}", tessDataPath)
    }

    fun performOcr(image: BufferedImage, language: String = "eng"): Result<String> {
        logger.debug("Performing OCR with language: {}", language)
        return runCatching {
            // Ensure thread safety if calling concurrently - Tesseract instance might not be thread-safe
            // synchronized(tesseract) { // Example synchronization if needed
                 tesseract.setLanguage(language)
                 tesseract.doOCR(image)
            // }
        }.onSuccess { text -> // Explicitly name result
             logger.debug("OCR successful, text length: {}", text.length)
        }.onFailure { exception -> // Explicitly name exception
            logger.error("Tesseract OCR failed for language {}", language, exception)
        }
    }
}

class PdfProcessor {
    // Consider making DPI configurable
    private val renderDpi = 300f

    fun extractImagesFromPdf(pdfPath: String): Result<List<BufferedImage>> {
        logger.debug("Extracting images from PDF: {}", pdfPath)
        val pdfFile = File(pdfPath)
        if (!pdfFile.exists()) {
             logger.error("PDF file not found: {}", pdfPath)
             return Result.failure(IllegalArgumentException("PDF file not found: $pdfPath"))
        }

        return runCatching {
            Loader.loadPDF(pdfFile).use { document: PDDocument -> // Use static Loader.loadPDF and explicitly type document
                if (document.isEncrypted) {
                    logger.warn("PDF is encrypted, attempting to process anyway: {}", pdfPath)
                    // Optionally try to decrypt with empty password, or fail here
                    // document.decrypt("") // Requires empty password or handling
                }
                val renderer = PDFRenderer(document)
                logger.debug("PDF has {} pages.", document.numberOfPages)
                (0 until document.numberOfPages).map { pageIndex ->
                    logger.debug("Rendering page {} with DPI {}", pageIndex + 1, renderDpi)
                    // This can be memory intensive for large PDFs / high DPI
                    renderer.renderImageWithDPI(pageIndex, renderDpi)
                }
            }
        }.onSuccess { imageList: List<BufferedImage> -> // Explicitly type lambda parameter
             logger.info("Successfully extracted {} images from PDF: {}", imageList.size, pdfPath)
        }.onFailure { exception: Throwable -> // Explicitly type lambda parameter
            logger.error("PDF processing failed for path: {}", pdfPath, exception)
        }
    }
}