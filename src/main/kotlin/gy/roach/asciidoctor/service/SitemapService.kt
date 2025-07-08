package gy.roach.asciidoctor.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name

@Service
class SitemapService {
    private val logger = LoggerFactory.getLogger(SitemapService::class.java)

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
     * Generate sitemap.adoc directly in the source directory before conversion
     */
    fun generateSitemapAdocInSourceDirectory(sourceDirectory: String): String? {
        val sourcePath = Paths.get(sourceDirectory)
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            return null
        }

        try {
            // Generate sitemap content based on existing .adoc files in source directory
            val sitemapAdocContent = generateSitemapAdocContentFromSource(sourcePath)
            val sitemapFile = sourcePath.resolve("sitemap.adoc")
            Files.write(sitemapFile, sitemapAdocContent.toByteArray())
            return sitemapFile.toString()
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate sitemap.adoc in source directory: $sourceDirectory", e)
        }
    }

    /**
     * Generate sitemap.adoc content based on source .adoc files
     */
    /**
     * Generate sitemap.adoc content based on source .adoc files
     */
    private fun generateSitemapAdocContentFromSource(sourcePath: Path): String {
        val adocFiles = findAdocFiles(sourcePath)
        val buttons = generateButtonsFromAdocFiles(adocFiles)

        // Copy sitemap icon to the source directory so it gets converted along with other files
        val iconPath = copySitemapIcon(sourcePath.toString())

        return buildSitemapAdocDocument(buttons, iconPath)
    }

    /**
     * Copy sitemap icon to target directory and return relative path
     */
    private fun copySitemapIcon(targetDirectory: String): String {
        return try {
            val targetPath = Paths.get(targetDirectory)
            val iconFileName = "sitemap-icon.svg"
            val targetIconPath = targetPath.resolve(iconFileName)

            // Copy the sitemap icon from resources to target directory
            val resourceStream = this::class.java.getResourceAsStream("/$iconFileName")
            if (resourceStream != null) {
                resourceStream.use { stream ->
                    Files.copy(stream, targetIconPath, StandardCopyOption.REPLACE_EXISTING)
                }
                logger.debug("Copied sitemap icon to: $targetIconPath")
                iconFileName // Return just the filename for relative reference
            } else {
                logger.warn("Sitemap icon not found in resources: /$iconFileName")
                "" // Return empty string if icon not found
            }
        } catch (e: Exception) {
            logger.error("Failed to copy sitemap icon to $targetDirectory", e)
            "" // Return empty string on error
        }
    }

    /**
     * Find all .adoc files in the source directory
     */
    private fun findAdocFiles(sourcePath: Path): List<Path> {
        val adocFiles = mutableListOf<Path>()

        try {
            Files.walk(sourcePath).use { stream ->
                stream.filter { path ->
                    Files.isRegularFile(path) &&
                            path.extension.equals("adoc", ignoreCase = true) &&
                            !path.name.equals("sitemap.adoc", ignoreCase = true) // Exclude the sitemap file itself
                }.sorted { a, b ->
                    a.name.compareTo(b.name)
                }.forEach { adocFiles.add(it) }
            }
        } catch (e: Exception) {
            // Log error but continue with empty list
        }

        return adocFiles
    }

    /**
     * Generate buttons from .adoc files
     */
    private fun generateButtonsFromAdocFiles(adocFiles: List<Path>): List<Map<String, Any>> {
        val buttons = mutableListOf<Map<String, Any>>()

        // Add home button if index.adoc exists
        val indexFile = adocFiles.find { it.name.equals("index.adoc", ignoreCase = true) }
        if (indexFile != null) {
            buttons.add(mapOf(
                "label" to "Home",
                "link" to "index.html",
                "description" to "Main page",
                "type" to "primary"
            ))
        }

        // Add buttons for other .adoc files
        adocFiles.filter { !it.name.equals("index.adoc", ignoreCase = true) }
            .forEach { file ->
                val fileName = file.name.removeSuffix(".adoc")
                val htmlFileName = "${fileName}.html"

                buttons.add(mapOf(
                    "label" to formatLabel(fileName),
                    "link" to htmlFileName,
                    "description" to "Page: ${formatLabel(fileName)}",
                    "type" to determineButtonType(fileName)
                ))
            }

        return buttons
    }

    /**
     * Generate and save interactive sitemap using DocOps hex buttons
     * Creates sitemap.adoc in the sourceDirectory which will be converted to target
     */
    fun generateAndSaveSitemap(outputDirectory: String, baseUrl: String = ""): String? {
        val outputPath = Paths.get(outputDirectory)
        if (!Files.exists(outputPath) || !Files.isDirectory(outputPath)) {
            return null
        }

        try {
            // Find the source directory by looking for the parent directory that contains .adoc files
            val sourceDirectory = findSourceDirectory(outputPath)
            if (sourceDirectory == null) {
                // If we can't find source directory, create sitemap.adoc in the output directory
                return generateSitemapAdocInDirectory(outputPath, baseUrl)
            }

            // Generate sitemap.adoc in the source directory
            val sitemapAdocContent = generateSitemapAdocContent(outputPath, baseUrl)
            val sitemapFile = sourceDirectory.resolve("sitemap.adoc")
            Files.write(sitemapFile, sitemapAdocContent.toByteArray())

            return sitemapFile.toString()
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate sitemap for directory: $outputDirectory", e)
        }
    }

    /**
     * Generate sitemap.adoc directly in the specified directory
     */
    private fun generateSitemapAdocInDirectory(directory: Path, baseUrl: String): String? {
        try {
            val sitemapAdocContent = generateSitemapAdocContent(directory, baseUrl)
            val sitemapFile = directory.resolve("sitemap.adoc")
            Files.write(sitemapFile, sitemapAdocContent.toByteArray())
            return sitemapFile.toString()
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate sitemap in directory: $directory", e)
        }
    }

    /**
     * Try to find the source directory by looking for a parent directory that contains .adoc files
     */
    private fun findSourceDirectory(outputPath: Path): Path? {
        var currentPath = outputPath.parent
        var maxDepth = 3 // Limit search depth to avoid infinite loops

        while (currentPath != null && maxDepth > 0) {
            // Check if this directory contains .adoc files
            try {
                Files.list(currentPath).use { stream ->
                    val hasAdocFiles = stream.anyMatch { file ->
                        Files.isRegularFile(file) && file.extension.equals("adoc", ignoreCase = true)
                    }
                    if (hasAdocFiles) {
                        return currentPath
                    }
                }
            } catch (e: Exception) {
                // Continue searching if we can't access this directory
            }

            currentPath = currentPath.parent
            maxDepth--
        }

        return null
    }

    /**
     * Generate AsciiDoc content with DocOps hex buttons for sitemap
     */
    private fun generateSitemapAdocContent(directory: Path, baseUrl: String): String {
        val sitemapTree = buildSitemapTree(directory)
        val buttons = generateButtonsFromTree(sitemapTree, baseUrl)

        // Copy sitemap icon to the target directory
        val iconPath = copySitemapIcon(directory.toString())

        return buildSitemapAdocDocument(buttons, iconPath)
    }

    /**
     * Build the sitemap tree structure
     */
    private fun buildSitemapTree(directory: Path): SitemapEntry {
        return buildSitemapEntry(directory)
    }

    /**
     * Generate button data from the sitemap tree
     */
    private fun generateButtonsFromTree(sitemapTree: SitemapEntry, baseUrl: String): List<Map<String, Any>> {
        val buttons = mutableListOf<Map<String, Any>>()

        // Add home button for index.html if it exists
        val indexPath = Paths.get(sitemapTree.path, "index.html")
        if (Files.exists(indexPath)) {
            buttons.add(mapOf(
                "label" to "Home",
                "link" to buildUrl(baseUrl, "index.html"),
                "description" to "Main page",
                "type" to "primary"
            ))
        }

        // Process all HTML files and directories
        addButtonsFromEntry(sitemapTree, buttons, baseUrl, "")

        return buttons
    }

    /**
     * Recursively add buttons from sitemap entries
     */
    private fun addButtonsFromEntry(entry: SitemapEntry, buttons: MutableList<Map<String, Any>>, baseUrl: String, parentPath: String) {
        // Skip root directory itself
        if (parentPath.isNotEmpty() || entry.name != "root") {
            val relativePath = if (parentPath.isEmpty()) entry.name else "$parentPath/${entry.name}"

            if (entry.isDirectory) {
                // Add directory as category button if it contains HTML files
                if (entry.children.any { !it.isDirectory }) {
                    buttons.add(mapOf(
                        "label" to formatLabel(entry.name),
                        "link" to buildUrl(baseUrl, relativePath),
                        "description" to "Section: ${formatLabel(entry.name)}",
                        "type" to "category"
                    ))
                }
            } else {
                // Add HTML file as content button
                val fileName = entry.name.removeSuffix(".html")
                if (fileName != "index" && fileName != "sitemap") { // Skip index and sitemap files
                    buttons.add(mapOf(
                        "label" to formatLabel(fileName),
                        "link" to buildUrl(baseUrl, relativePath),
                        "description" to "Page: ${formatLabel(fileName)}",
                        "type" to determineButtonType(fileName)
                    ))
                }
            }
        }

        // Process children
        entry.children.forEach { child ->
            val childPath = if (parentPath.isEmpty()) entry.name else "$parentPath/${entry.name}"
            addButtonsFromEntry(child, buttons, baseUrl, if (entry.name == "root") "" else childPath)
        }
    }

    /**
     * Format label for display
     */
    private fun formatLabel(name: String): String {
        return name.split("-", "_")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }

    /**
     * Determine button type based on file name or content
     */
    private fun determineButtonType(fileName: String): String {
        return when {
            fileName.contains("about", ignoreCase = true) -> "info"
            fileName.contains("contact", ignoreCase = true) -> "support"
            fileName.contains("help", ignoreCase = true) -> "support"
            fileName.contains("doc", ignoreCase = true) -> "support"
            fileName.contains("faq", ignoreCase = true) -> "support"
            fileName.contains("guide", ignoreCase = true) -> "support"
            fileName.contains("product", ignoreCase = true)||fileName.contains("story", ignoreCase = true) ||fileName.contains("arch", ignoreCase = true) -> "product"
            fileName.contains("service", ignoreCase = true) -> "service"
            fileName.contains("blog", ignoreCase = true) -> "content"
            fileName.contains("news", ignoreCase = true) -> "content"
            fileName.contains("resource", ignoreCase = true) -> "content"
            fileName.contains("download", ignoreCase = true) -> "content"
            else -> "content"
        }
    }

    /**
     * Build URL from base URL and relative path
     */
    private fun buildUrl(baseUrl: String, relativePath: String): String {
        return if (baseUrl.isEmpty()) {
            relativePath
        } else {
            "${baseUrl.trimEnd('/')}/$relativePath"
        }
    }

    /**
     * Build the complete AsciiDoc document with DocOps hex buttons
     */
    private fun buildSitemapAdocDocument(buttons: List<Map<String, Any>>, iconPath: String = ""): String {
        val buttonsJson = generateButtonsJson(buttons, iconPath)

        return """
= Website Sitemap
:icons: font
:docname: sitemap


== Interactive Sitemap

This sitemap provides a visual navigation structure of the website using hexagonal buttons.

[docops,buttons]
----
$buttonsJson
----

== Navigation Guide

* Click any hexagonal button to navigate to that page
* Hover over buttons to see page descriptions
* Different colors represent different types of content:
  - 🔴 **Primary**: Main entry points (Home)
  - 🔵 **Category**: Section directories
  - 🟢 **Product**: Product-related pages
  - 🟣 **Service**: Service-related pages
  - 🟠 **Support**: Help and documentation
  - ⚫ **Info**: About and company information
  - 🟦 **Content**: Blog, news, and resources
  - 🔵 **Page**: General pages

== About This Sitemap

This sitemap was automatically generated from the website structure and includes all accessible pages. The visualization uses DocOps hex buttons for an interactive navigation experience.

Generated on: {localdate} at {localtime}
    """.trimIndent()
    }

    /**
     * Generate JSON for DocOps buttons
     */
    private fun generateButtonsJson(buttons: List<Map<String, Any>>, iconPath: String = ""): String {
        val buttonsJsonArray = buttons.joinToString(",\n    ") { button ->
            val embeddedImageJson = if (iconPath.isNotEmpty()) {
                """
                "embeddedImage": {
                  "ref": "$iconPath"
                },"""
                } else {
                    ""
            }
            """
    {
      "label": "${button["label"]}",
      "link": "${button["link"]}",
      "description": "${button["description"]}",
      $embeddedImageJson
      "type": "${button["type"]}"
    }
        """.trimIndent()
        }



        return """
{
  "buttons": [
    $buttonsJsonArray
  ],
  "buttonType": "HEX",
  "theme": {
    "hexLinesEnabled": true,
    "strokeColor": "#2c3e50",
    "colorTypeMap": {
      "primary": "#e74c3c",
      "category": "#3498db",
      "product": "#27ae60",
      "service": "#9b59b6",
      "support": "#f39c12",
      "info": "#34495e",
      "content": "#16a085",
      "page": "#98A1BC"
    },
    "scale": 1.0,
    "columns": 5,
    "buttonStyle": {
      "labelStyle": "font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; font-size: 36px; font-weight: 600; fill: #ffffff;",
      "descriptionStyle": "font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; font-size: 12px; fill: #ffffff; opacity: 0.9;"
    }
  }
}
    """.trimIndent()
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
            return generateSitemapAdocContent(path, baseUrl)
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
        return generateAndSaveSitemap(outputDirectory, baseUrl)
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

    /**
     * Delete the generated sitemap.adoc file from the source directory
     */
    fun deleteSitemapAdocFromSource(sourceDirectory: String): Boolean {
        return try {
            val sourcePath = Paths.get(sourceDirectory)
            val sitemapFile = sourcePath.resolve("sitemap.adoc")

            if (Files.exists(sitemapFile)) {
                Files.delete(sitemapFile)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

}