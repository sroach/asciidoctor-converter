package gy.roach.asciidoctor.service

import gy.roach.asciidoctor.config.ConverterSettings
import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption


@Service
class AsciiDoctorConverter(private val converterSettings: ConverterSettings) {
    val asciidoctor: Asciidoctor = Asciidoctor.Factory.create()

    init {
        asciidoctor.requireLibrary("asciidoctor-diagram")
    }

    fun convert(files: List<File>, toDir: String) {
        files.forEach { file ->
            val targetFile = File(toDir, file.name)
            val targetHtmlFile = File(toDir, file.nameWithoutExtension + ".html")

            // Check if conversion is needed
            val shouldConvert = shouldConvertFile(file, targetFile, targetHtmlFile)

            if (shouldConvert) {
                val options = buildOptions(buildAttributes())
                options.setMkDirs(true)
                options.setToDir(toDir)
                asciidoctor.convertFile(file, options)

                // Copy the source .adoc file to the target directory
                Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun shouldConvertFile(sourceFile: File, targetAdocFile: File, targetHtmlFile: File): Boolean {
        // If target adoc file doesn't exist, conversion is needed
        if (!targetAdocFile.exists()) {
            return true
        }

        // If target HTML file doesn't exist, conversion is needed
        if (!targetHtmlFile.exists()) {
            return true
        }

        // Compare file contents of adoc files
        return Files.mismatch(sourceFile.toPath(), targetAdocFile.toPath()) != -1L
    }

    private fun buildAttributes(): Attributes {
        return Attributes.builder()
            .sourceHighlighter("highlightjs")
            .allowUriRead(true)
            .linkAttrs(true)
            .attribute("local-debug", converterSettings.localDebug.toString())
            .attribute("panel-server", converterSettings.panelServer)
            .attribute("panel-webserver", converterSettings.panelWebserver)
            .dataUri(true)
            .copyCss(true)
            .noFooter(true)
            .build()
    }
    private fun buildOptions(attrs: Attributes): Options {
        return  Options.builder()
            .backend("html")
            .attributes(attrs)
            .safe(SafeMode.UNSAFE)
            .build()
    }
}
