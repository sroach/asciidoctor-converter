package gy.roach.asciidoctor.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.extension
import kotlin.io.path.name

@Service
class SitemapService {
    private val logger = LoggerFactory.getLogger(SitemapService::class.java)

    @Value("\${sitemap.directory-depth:2}")
    private val defaultDirectoryDepth: Int = 2

    data class SitemapEntry(
        val path: String,
        val name: String,
        val isDirectory: Boolean,
        val children: List<SitemapEntry> = emptyList(),
        val depth: Int = 0
    )

    fun scanDirectory(directoryPath: String, maxDepth: Int = defaultDirectoryDepth): SitemapEntry? {
        val path = Paths.get(directoryPath)
        if (!Files.exists(path)) {
            return null
        }

        return buildSitemapEntry(path, maxDepth, 0)
    }

    /**
     * Generate sitemap.adoc directly in the source directory before conversion
     */
    fun generateSitemapAdocInSourceDirectory(sourceDirectory: String, maxDepth: Int = defaultDirectoryDepth): String? {
        val sourcePath = Paths.get(sourceDirectory)
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            return null
        }

        try {
            // Generate sitemap content based on directory structure in source directory
            val sitemapAdocContent = generateSitemapAdocContentFromSource(sourcePath, maxDepth)
            val sitemapFile = sourcePath.resolve("sitemap.adoc")
            Files.write(sitemapFile, sitemapAdocContent.toByteArray())
            return sitemapFile.toString()
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate sitemap.adoc in source directory: $sourceDirectory", e)
        }
    }

    /**
     * Generate sitemap.adoc content based on directory structure
     */
    private fun generateSitemapAdocContentFromSource(sourcePath: Path, maxDepth: Int): String {
        val directories = findDirectories(sourcePath, maxDepth)
        val buttons = generateButtonsFromDirectories(directories)

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
     * Find all directories in the source directory up to specified depth
     */
    private fun findDirectories(sourcePath: Path, maxDepth: Int): List<DirectoryInfo> {
        val directories = mutableListOf<DirectoryInfo>()

        try {
            scanDirectoriesRecursively(sourcePath, directories, maxDepth, 0, "")
        } catch (e: Exception) {
            logger.error("Error scanning directories in $sourcePath", e)
        }

        return directories.sortedBy { it.relativePath }
    }

    /**
     * Recursively scan directories up to the specified depth
     */
    private fun scanDirectoriesRecursively(
        currentPath: Path,
        directories: MutableList<DirectoryInfo>,
        maxDepth: Int,
        currentDepth: Int,
        relativePath: String
    ) {
        if (currentDepth >= maxDepth) {
            return
        }

        try {
            Files.list(currentPath).use { stream ->
                stream.filter { Files.isDirectory(it) }
                    .sorted { a, b -> a.name.compareTo(b.name) }
                    .forEach { directory ->
                        val dirName = directory.name
                        val newRelativePath = if (relativePath.isEmpty()) dirName else "$relativePath/$dirName"

                        directories.add(DirectoryInfo(
                            name = dirName,
                            relativePath = newRelativePath,
                            absolutePath = directory.toString(),
                            depth = currentDepth + 1
                        ))

                        // Recursively scan subdirectories
                        scanDirectoriesRecursively(
                            directory,
                            directories,
                            maxDepth,
                            currentDepth + 1,
                            newRelativePath
                        )
                    }
            }
        } catch (e: Exception) {
            logger.warn("Failed to scan directory: $currentPath", e)
        }
    }

    /**
     * Data class to hold directory information
     */
    data class DirectoryInfo(
        val name: String,
        val relativePath: String,
        val absolutePath: String,
        val depth: Int
    )

    /**
     * Generate buttons from directories
     */
    private fun generateButtonsFromDirectories(directories: List<DirectoryInfo>): List<Map<String, Any>> {
        val buttons = mutableListOf<Map<String, Any>>()

        // Add home button for root directory
        buttons.add(mapOf(
            "label" to "Home",
            "link" to "index.html",
            "description" to "Main page",
            "type" to "primary"
        ))

        // Add buttons for each directory
        directories.forEach { directory ->
            buttons.add(mapOf(
                "label" to formatLabel(directory.name),
                "link" to "${directory.relativePath}/sitemap.html",
                "description" to "Section: ${formatLabel(directory.name)} (Depth: ${directory.depth})",
                "type" to determineButtonTypeFromDirectory(directory.name)
            ))
        }

        return buttons
    }

    /**
     * Generate and save interactive sitemap using DocOps hex buttons
     * Creates sitemap.adoc in the sourceDirectory which will be converted to target
     */
    fun generateAndSaveSitemap(outputDirectory: String, baseUrl: String = "", maxDepth: Int = defaultDirectoryDepth): String? {
        val outputPath = Paths.get(outputDirectory)
        if (!Files.exists(outputPath) || !Files.isDirectory(outputPath)) {
            return null
        }

        try {
            // Find the source directory by looking for the parent directory that contains .adoc files
            val sourceDirectory = findSourceDirectory(outputPath)
            if (sourceDirectory == null) {
                // If we can't find source directory, create sitemap.adoc in the output directory
                return generateSitemapAdocInDirectory(outputPath, baseUrl, maxDepth)
            }

            // Generate sitemap.adoc in the source directory
            val sitemapAdocContent = generateSitemapAdocContent(outputPath, baseUrl, maxDepth)
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
    private fun generateSitemapAdocInDirectory(directory: Path, baseUrl: String, maxDepth: Int): String? {
        try {
            val sitemapAdocContent = generateSitemapAdocContent(directory, baseUrl, maxDepth)
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
    private fun generateSitemapAdocContent(directory: Path, baseUrl: String, maxDepth: Int): String {
        val sitemapTree = buildSitemapTree(directory, maxDepth)
        val buttons = generateButtonsFromTree(sitemapTree, baseUrl)

        // Copy sitemap icon to the target directory
        val iconPath = copySitemapIcon(directory.toString())

        return buildSitemapAdocDocument(buttons, iconPath)
    }

    /**
     * Build the sitemap tree structure
     */
    private fun buildSitemapTree(directory: Path, maxDepth: Int): SitemapEntry {
        return buildSitemapEntry(directory, maxDepth, 0)
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

        // Process all directories based on depth
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
                // Add directory as category button
                buttons.add(mapOf(
                    "label" to formatLabel(entry.name),
                    "link" to buildUrl(baseUrl, "$relativePath/index.html"),
                    "description" to "Section: ${formatLabel(entry.name)} (Depth: ${entry.depth})",
                    "type" to determineButtonTypeFromDirectory(entry.name)
                ))
            }
        }

        // Process children directories only
        entry.children.filter { it.isDirectory }.forEach { child ->
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
     * Determine button type based on directory name
     */
    private fun determineButtonTypeFromDirectory(dirName: String): String {
        return when {
            dirName.contains("about", ignoreCase = true) -> "info"
            dirName.contains("contact", ignoreCase = true) -> "support"
            dirName.contains("help", ignoreCase = true) -> "support"
            dirName.contains("doc", ignoreCase = true) -> "support"
            dirName.contains("faq", ignoreCase = true) -> "support"
            dirName.contains("guide", ignoreCase = true) -> "support"
            dirName.contains("product", ignoreCase = true) || dirName.contains("story", ignoreCase = true) || dirName.contains("arch", ignoreCase = true) -> "product"
            dirName.contains("service", ignoreCase = true) -> "service"
            dirName.contains("blog", ignoreCase = true) -> "content"
            dirName.contains("news", ignoreCase = true) -> "content"
            dirName.contains("resource", ignoreCase = true) -> "content"
            dirName.contains("download", ignoreCase = true) -> "content"
            else -> "category"
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


== Interactive Directory Sitemap

This sitemap provides a visual navigation structure of the website directories using hexagonal buttons.

[docops,buttons]
----
$buttonsJson
----

== Navigation Guide

* Click any hexagonal button to navigate to that directory
* Hover over buttons to see directory descriptions
* Different colors represent different types of content:
  - 🔴 **Primary**: Main entry points (Home)
  - 🔵 **Category**: Directory sections
  - 🟢 **Product**: Product-related directories
  - 🟣 **Service**: Service-related directories
  - 🟠 **Support**: Help and documentation directories
  - ⚫ **Info**: About and company information directories
  - 🟦 **Content**: Blog, news, and resources directories

== Directory Structure

This sitemap was generated based on the directory structure with a maximum depth of directories traversed. Each button represents a directory that may contain content or further subdirectories.

== About This Sitemap

This sitemap was automatically generated from the website directory structure. The visualization uses DocOps hex buttons for an interactive navigation experience.

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
    fun generateSitemapContent(directoryPath: String, baseUrl: String = "", maxDepth: Int = defaultDirectoryDepth): String? {
        val path = Paths.get(directoryPath)
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            return null
        }

        try {
            return generateSitemapAdocContent(path, baseUrl, maxDepth)
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate sitemap content for directory: $directoryPath", e)
        }
    }

    /**
     * Generate and save sitemap with custom base URL and depth
     */
    fun generateAndSaveCustomSitemap(
        outputDirectory: String,
        baseUrl: String = "",
        maxDepth: Int = defaultDirectoryDepth
    ): String? {
        return generateAndSaveSitemap(outputDirectory, baseUrl, maxDepth)
    }

    private fun buildSitemapEntry(path: Path, maxDepth: Int, currentDepth: Int): SitemapEntry {
        val children = if (Files.isDirectory(path) && currentDepth < maxDepth) {
            try {
                Files.list(path).use { stream ->
                    stream.filter { child ->
                        Files.isDirectory(child)
                    }.sorted { a, b ->
                        a.name.compareTo(b.name)
                    }.map { child ->
                        buildSitemapEntry(child, maxDepth, currentDepth + 1)
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
            children = children,
            depth = currentDepth
        )
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