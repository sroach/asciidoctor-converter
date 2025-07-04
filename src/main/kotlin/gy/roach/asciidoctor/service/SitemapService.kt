package gy.roach.asciidoctor.service

import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name

@Service
class SitemapService {

    data class SitemapEntry(
        val path: String,
        val name: String,
        val isDirectory: Boolean,
        val children: List<SitemapEntry> = emptyList()
    )

    fun scanDirectory(directoryPath: String): SitemapEntry? {
        val path = Paths.get(directoryPath)
        if (!Files.exists(path)) {
            return null
        }

        return buildSitemapEntry(path)
    }

    /**
     * Generate and save interactive sitemap HTML file for the given directory
     */
    fun generateAndSaveSitemap(outputDirectory: String, baseUrl: String = ""): String? {
        val outputPath = Paths.get(outputDirectory)
        if (!Files.exists(outputPath) || !Files.isDirectory(outputPath)) {
            return null
        }

        try {
            val sitemapGenerator = SitemapHtmlGenerator(baseUrl = baseUrl)
            val htmlContent = sitemapGenerator.generateSitemap(outputPath)

            // Save sitemap.html in the root of the output directory
            val sitemapFile = outputPath.resolve("sitemap.html")
            Files.write(sitemapFile, htmlContent.toByteArray())

            return sitemapFile.toString()
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate sitemap for directory: $outputDirectory", e)
        }
    }

    /**
     * Generate interactive sitemap content without saving to file
     */
    fun generateSitemapContent(directoryPath: String, baseUrl: String = ""): String? {
        val path = Paths.get(directoryPath)
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            return null
        }

        try {
            val sitemapGenerator = SitemapHtmlGenerator(baseUrl = baseUrl)
            return sitemapGenerator.generateSitemap(path)
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate sitemap content for directory: $directoryPath", e)
        }
    }

    /**
     * Generate and save sitemap with custom base URL
     */
    fun generateAndSaveCustomSitemap(
        outputDirectory: String,
        baseUrl: String = ""
    ): String? {
        val outputPath = Paths.get(outputDirectory)
        if (!Files.exists(outputPath) || !Files.isDirectory(outputPath)) {
            return null
        }

        try {
            val sitemapGenerator = SitemapHtmlGenerator(baseUrl = baseUrl)
            val htmlContent = sitemapGenerator.generateSitemap(outputPath)

            val sitemapFile = outputPath.resolve("sitemap.html")
            Files.write(sitemapFile, htmlContent.toByteArray())

            return sitemapFile.toString()
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate custom sitemap for directory: $outputDirectory", e)
        }
    }

    private fun buildSitemapEntry(path: Path): SitemapEntry {
        val children = if (Files.isDirectory(path)) {
            try {
                Files.list(path).use { stream ->
                    stream.filter { child ->
                        Files.isDirectory(child) || isHtmlFile(child)
                    }.sorted { a, b ->
                        when {
                            Files.isDirectory(a) && !Files.isDirectory(b) -> -1
                            !Files.isDirectory(a) && Files.isDirectory(b) -> 1
                            else -> a.name.compareTo(b.name)
                        }
                    }.map { child ->
                        buildSitemapEntry(child)
                    }.toList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        return SitemapEntry(
            path = path.toString(),
            name = if (path.name.isBlank()) "root" else path.name,
            isDirectory = Files.isDirectory(path),
            children = children
        )
    }

    private fun isHtmlFile(path: Path): Boolean {
        val extension = path.extension.lowercase()
        return extension in setOf("html", "htm", "xhtml")
    }
}