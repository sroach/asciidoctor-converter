package gy.roach.asciidoctor.web

import gy.roach.asciidoctor.service.AsciiDoctorConverter
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.openpdf.pdf.ITextRenderer
import org.openpdf.text.DocumentException
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

data class PdfRenderFromHtmlRequest(
    @field:NotBlank
    @field:Pattern(
        regexp = "^(?!/)(?!.*\\.\\.)[A-Za-z0-9._\\-/]+\\.html$",
        message = "htmlRelPath must be a safe relative .html path"
    )
    val htmlRelPath: String,
    val disposition: Disposition = Disposition.ATTACHMENT
)

enum class Disposition { INLINE, ATTACHMENT }

@RestController
@RequestMapping("/api/pdf")
class PdfController(
    private val asciiDoctorConverter: AsciiDoctorConverter,
    @Value("\${github.web.directory}") private val webRootDir: String
) {
    @PostMapping(
        "/render-from-html",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_PDF_VALUE]
    )
    fun renderFromHtml(@RequestBody @Valid request: PdfRenderFromHtmlRequest): ResponseEntity<Resource> {
        val base = Path.of(webRootDir).toAbsolutePath().normalize()
        val htmlPath = base.resolve(request.htmlRelPath).normalize()

        require(htmlPath.startsWith(base)) { "htmlRelPath escapes base directory" }
        require(Files.exists(htmlPath) && Files.isRegularFile(htmlPath)) { "HTML file not found" }

        val adocPath = htmlPath.resolveSibling("${htmlPath.fileName.toString().removeSuffix(".html")}.adoc")
        val mdPath = htmlPath.resolveSibling("${htmlPath.fileName.toString().removeSuffix(".html")}.md")

        val pdfPath: Path = when {
            Files.exists(adocPath) -> {
                asciiDoctorConverter.convertSingleFileToPdf(adocPath.toFile())
                adocPath.resolveSibling("${adocPath.fileName.toString().removeSuffix(".adoc")}.pdf")
            }
            Files.exists(mdPath) -> {
                // for markdown: read html and call your markdown htmlToPdf flow
                val html = Files.readString(htmlPath)
                // markdownConverter.htmlToPdf(Files.readString(htmlPath), ...)
                val out = mdPath.resolveSibling("${mdPath.fileName.toString().removeSuffix(".md")}.pdf")
                htmlToPdf(html, out)
                out
            }
            else -> error("No matching .adoc or .md source next to html")
        }

        require(Files.exists(pdfPath)) { "PDF generation failed" }

        val bytes = Files.readAllBytes(pdfPath)
        val dispositionType = if (request.disposition == Disposition.INLINE) "inline" else "attachment"
        val fileName = pdfPath.fileName.toString()

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "$dispositionType; filename=\"$fileName\"")
            .body(ByteArrayResource(bytes))
    }

    @Throws(IOException::class, DocumentException::class)
    fun htmlToPdf(html: String, outputPdfPath: Path) {
        val iTextRenderer = ITextRenderer()
        iTextRenderer.setDocumentFromString(html)
        iTextRenderer.layout()
        iTextRenderer.createPDF(FileOutputStream(outputPdfPath.toFile()))

    }
}

