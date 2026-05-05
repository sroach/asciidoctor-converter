package gy.roach.asciidoctor.web

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
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
import java.io.File
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

    @GetMapping("/render-from-html",produces = [MediaType.APPLICATION_PDF_VALUE])
    fun toPdf( @RequestParam("outputDir") outputDirectory: String, @RequestParam("htmlRelPath") htmlRelPath: String): ResponseEntity<Resource> {
        val source = File(outputDirectory, htmlRelPath)
        val pdfPath = when {
            source.name.endsWith("html") -> {
                val html = source.readText()
                val out = source.resolveSibling("${source.name.toString().removeSuffix(".md")}.pdf")
                htmlToPdf(html, out.toPath())
                out
            }
            source.name.endsWith("adoc") -> {
                asciiDoctorConverter.convertSingleFileToPdf(source)
                source.resolveSibling("${source.name.toString().removeSuffix(".adoc")}.pdf")
            }
            else -> error("No matching .adoc or .md source next to html")
        }
        val dispositionType = "inline"
        val bytes = pdfPath.readBytes()
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "$dispositionType; filename=\"${pdfPath.name}\"")
            .body(ByteArrayResource(bytes))
    }
    @Throws(IOException::class, DocumentException::class)
    fun htmlToPdf(html: String, outputPdfPath: Path) {
        val iTextRenderer = ITextRenderer()
        iTextRenderer.setDocumentFromString(html)
        iTextRenderer.sharedContext.media = "pdf"
        iTextRenderer.sharedContext.isInteractive = false
        iTextRenderer.sharedContext.textRenderer.setSmoothingThreshold(0.0f)
        iTextRenderer.layout()
        iTextRenderer.createPDF(FileOutputStream(outputPdfPath.toFile()))

        /*val pdfBuilder = PdfRendererBuilder()
        pdfBuilder.useFastMode()
        pdfBuilder.withHtmlContent(html, null)
        pdfBuilder.toStream(FileOutputStream(outputPdfPath.toFile()))
        pdfBuilder.run()*/
    }
}

