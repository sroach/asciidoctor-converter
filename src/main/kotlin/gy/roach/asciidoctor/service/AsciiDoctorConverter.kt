package gy.roach.asciidoctor.service

import gy.roach.asciidoctor.config.ConverterSettings
import gy.roach.asciidoctor.extension.CopyToClipboardDocinfoProcessor
import gy.roach.asciidoctor.extension.DocOpsMermaidDocinfoProcessor
import gy.roach.asciidoctor.extension.MermaidIncludeDocinfoProcessor
import gy.roach.asciidoctor.extension.ReadingTimeDocinfoProcessor
import gy.roach.asciidoctor.tabs.BlockSwitchDocinfoProcessor
import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.AttributesBuilder
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

data class ConversionStats(
    var filesNeedingConversion: Int = 0,
    var filesConverted: Int = 0,
    var filesFailed: Int = 0,
    var filesDeleted: Int = 0,
    var filesCopied: Int = 0,
    val failedFiles: MutableList<String> = mutableListOf(),
    val deletedFiles: MutableList<String> = mutableListOf(),
    val copiedFiles: MutableList<String> = mutableListOf()
)


@Service
class AsciiDoctorConverter(private val converterSettings: ConverterSettings,
                           private val readingTimeDocinfoProcessor: ReadingTimeDocinfoProcessor,
                           private val copyToClipboardDocinfoProcessor: CopyToClipboardDocinfoProcessor,
                           private val markdownConverter: MarkdownConverter ) {
    val asciidoctor: Asciidoctor = Asciidoctor.Factory.create()
    private val logger = LoggerFactory.getLogger(AsciiDoctorConverter::class.java)

    // Pattern to match include directives in Asciidoctor files
    private val includePattern = Pattern.compile("include::([^\\[\\]]+)(?:\\[.*?\\])?")

    init {
        asciidoctor.requireLibrary("asciidoctor-diagram")
        asciidoctor.javaExtensionRegistry().docinfoProcessor(BlockSwitchDocinfoProcessor::class.java)
        asciidoctor.javaExtensionRegistry().docinfoProcessor(readingTimeDocinfoProcessor)
        asciidoctor.javaExtensionRegistry().docinfoProcessor(copyToClipboardDocinfoProcessor)
        asciidoctor.javaExtensionRegistry().docinfoProcessor(DocOpsMermaidDocinfoProcessor::class.java)
        asciidoctor.javaExtensionRegistry().docinfoProcessor(MermaidIncludeDocinfoProcessor::class.java)

        asciidoctor.rubyExtensionRegistry().loadClass(AsciiDoctorConverter::class.java.getResourceAsStream("/lib/docops-extension.rb"))
        asciidoctor.rubyExtensionRegistry().loadClass(AsciiDoctorConverter::class.java.getResourceAsStream("/lib/reactions_block_processor.rb"))
        asciidoctor.rubyExtensionRegistry().loadClass(AsciiDoctorConverter::class.java.getResourceAsStream("/lib/page_navigation_postprocessor.rb"))
    }

    /**
     * Parses an Asciidoctor file and extracts all include directives.
     * Returns a set of File objects representing the included files.
     */
    private fun extractIncludes(file: File): Set<File> {
        if (!file.exists() || !file.isFile) {
            return emptySet()
        }

        val content = file.readText()
        val includes = mutableSetOf<File>()
        val matcher = includePattern.matcher(content)

        while (matcher.find()) {
            val includePath = matcher.group(1)
            // Resolve the include path relative to the parent file
            val parentDir = file.parentFile
            val includedFile = File(parentDir, includePath)

            if (includedFile.exists() && includedFile.isFile) {
                includes.add(includedFile)
                // Only recursively extract includes from AsciiDoc files to avoid infinite loops
                // Non-AsciiDoc files (like JSON, XML, etc.) are leaf nodes in the dependency tree
                if (includedFile.extension == "adoc") {
                    includes.addAll(extractIncludes(includedFile))
                }
            } else {
                logger.warn("Included file not found: $includePath in ${file.name}")
            }
        }

        return includes
    }

    /**
     * Builds a comprehensive dependency map between files and their includes.
     * This map includes both AsciiDoc and non-AsciiDoc files that are included.
     *
     * @param adocFiles List of AsciiDoc files to analyze
     * @param allSourceFiles List of all source files (used for tracking non-AsciiDoc includes)
     * @return Pair of maps: first is file dependencies, second is reverse dependencies
     */
    private fun buildDependencyMap(adocFiles: List<File>, allSourceFiles: List<File>): Pair<Map<File, Set<File>>, Map<File, Set<File>>> {
        val fileDependencies = mutableMapOf<File, MutableSet<File>>()
        val reverseDependencies = mutableMapOf<File, MutableSet<File>>()

        // Build direct dependencies for all AsciiDoc files
        adocFiles.forEach { file ->
            fileDependencies[file] = extractIncludes(file).toMutableSet()
        }

        // Build reverse dependencies (which files include this file)
        fileDependencies.forEach { (parent, includes) ->
            includes.forEach { include ->
                if (!reverseDependencies.containsKey(include)) {
                    reverseDependencies[include] = mutableSetOf()
                }
                reverseDependencies[include]?.add(parent)
            }
        }

        val totalIncludes = fileDependencies.values.sumOf { it.size }
        val adocIncludes = fileDependencies.values.sumOf { includes -> includes.count { it.extension == "adoc" } }
        val nonAdocIncludes = totalIncludes - adocIncludes

        logger.info("Built dependency map for ${adocFiles.size} AsciiDoc files, found $totalIncludes include relationships " +
                "($adocIncludes .adoc includes, $nonAdocIncludes non-.adoc includes)")

        return Pair(fileDependencies, reverseDependencies)
    }

    /**
     * Determines if a file needs to be converted based on:
     * 1. If the target .adoc file doesn't exist.
     * 2. If the target HTML file doesn't exist
     * 3. If the content of the source and target .adoc files differs.
     * 4. If any of the included files (including non-AsciiDoc) have changed.
     */
    private fun shouldConvertFile(
        sourceFile: File,
        targetAdocFile: File,
        targetHtmlFile: File,
        fileDependencies: Map<File, Set<File>>
    ): Boolean {
        // If target adoc file doesn't exist, conversion is needed
        if (!targetAdocFile.exists()) {
            logger.debug("Target adoc file doesn't exist for ${sourceFile.name}, conversion needed")
            return true
        }

        // If target HTML file doesn't exist, conversion is needed
        if (!targetHtmlFile.exists()) {
            logger.debug("Target HTML file doesn't exist for ${sourceFile.name}, conversion needed")
            return true
        }

        // Compare file contents of adoc files
        val contentDiffers = Files.mismatch(sourceFile.toPath(), targetAdocFile.toPath()) != -1L
        if (contentDiffers) {
            logger.debug("Content differs for ${sourceFile.name}, conversion needed")
            return true
        }

        // Check if any included files are newer than the target HTML file
        val includes = fileDependencies[sourceFile] ?: emptySet()
        val targetHtmlLastModified = targetHtmlFile.lastModified()

        for (include in includes) {
            if (include.exists() && include.lastModified() > targetHtmlLastModified) {
                logger.debug("Included file ${include.name} is newer than target HTML for ${sourceFile.name}, conversion needed")
                return true
            }
        }

        return false
    }



    private fun buildAttributes(): Attributes {
        // Get the docinfo directory from resources
        val docinfoDir = this::class.java.classLoader.getResource("docinfo")?.path
            ?: "src/main/resources/docinfo"

        return Attributes.builder()
            .sourceHighlighter("highlightjs")
            .allowUriRead(true)
            .linkAttrs(true)
            .docType("book")
            .attribute("local-debug", converterSettings.localDebug.toString())
            .attribute("panel-server", converterSettings.panelServer)
            .attribute("panel-webserver", converterSettings.panelWebserver)
            .dataUri(true)
            .copyCss(true)
            .noFooter(true)
            .attribute("docinfodir", docinfoDir)
            .attribute("docinfo", "shared")
            .attribute("encoding", "utf-8")
            .attribute("pdf-theme", "uri:classloader:/themes/basic-theme.yml")
            .attribute("pdf-fontsdir", "uri:classloader:/fonts;GEM_FONTS_DIR")
            .build()

    }
    private fun buildOptions(attrs: Attributes): Options {
        return  Options.builder()
            .backend("html")
            .attributes(attrs)
            .safe(SafeMode.UNSAFE)
            .build()
    }

    private fun buildPdfOptions(attrs: Attributes): Options {
        return Options.builder()
            .backend("pdf")
            .attributes(attrs)
            .safe(SafeMode.UNSAFE)
            .build()
    }



    /**
     * Asynchronously converts a list of files to PDF format.
     * 
     * @param files The list of AsciiDoc files to convert
     * @param toDir The directory where the PDFs will be written
     * @return A CompletableFuture that will complete when all conversions are done
     */
    @Async
    fun convertToPdfAsync(files: List<File>, toDir: String): CompletableFuture<ConversionStats> {
        val stats = ConversionStats()
        val future = CompletableFuture<ConversionStats>()

        try {
            // Limit the number of concurrent PDF conversions to avoid memory issues
            val maxConcurrentConversions = 2
            val executor = Executors.newFixedThreadPool(maxConcurrentConversions)

            val futures = files.map { file ->
                CompletableFuture.supplyAsync({
                    try {
                        val targetDir = File(toDir)
                        if (!targetDir.exists()) {
                            targetDir.mkdirs()
                        }

                        // Calculate the output PDF file path
                        val pdfFileName = file.nameWithoutExtension + ".pdf"
                        val targetFile = File(targetDir, pdfFileName)

                        // Create parent directories if they don't exist
                        targetFile.parentFile?.mkdirs()
                        val attrs = buildAttributes()
                        val options = buildPdfOptions(attrs)
                        options.setMkDirs(true)

                        // Set the output directory to the parent directory of the target file
                        options.setToDir(targetFile.parentFile.absolutePath)

                        // Convert the file with a timeout
                        val localAsciidoctor = Asciidoctor.Factory.create()
                        localAsciidoctor.requireLibrary("asciidoctor-diagram")

                        localAsciidoctor.rubyExtensionRegistry().loadClass(AsciiDoctorConverter::class.java.getResourceAsStream("/lib/docops-extension.rb"))

                        localAsciidoctor.convertFile(file, options)

                        synchronized(stats) {
                            stats.filesConverted++
                        }
                        logger.info("Successfully converted file to PDF: ${file.name}")
                        true
                    } catch (e: Exception) {
                        synchronized(stats) {
                            stats.filesFailed++
                            stats.failedFiles.add(file.name)
                        }
                        logger.error("Failed to convert file to PDF: ${file.name}", e)
                        false
                    }
                }, executor)
            }

            // Wait for all conversions to complete with a timeout
            CompletableFuture.allOf(*futures.toTypedArray())
                .orTimeout(30, TimeUnit.MINUTES)
                .whenComplete { _, throwable ->
                    executor.shutdown()
                    if (throwable != null) {
                        logger.error("PDF conversion failed", throwable)
                        future.completeExceptionally(throwable)
                    } else {
                        logger.info("PDF conversion completed: ${stats.filesConverted} files converted, ${stats.filesFailed} files failed")
                        future.complete(stats)
                    }
                }
        } catch (e: Exception) {
            logger.error("PDF conversion failed", e)
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Gets all .adoc files from a directory recursively.
     *
     * @param directory The directory to search for .adoc files
     * @return List of .adoc files found in the directory
     */
    private fun getAdocFiles(directory: File): List<File> {
        if (!directory.exists() || !directory.isDirectory) {
            logger.warn("Source directory does not exist: ${directory.absolutePath}")
            return emptyList()
        }

        return directory.walkTopDown()
            .filter { it.isFile && it.extension == "adoc" }
            .toList()
    }

    /**
     * Gets all non-AsciiDoc files from a directory recursively.
     *
     * @param directory The directory to search for non-AsciiDoc files
     * @return List of non-AsciiDoc files found in the directory
     */
    private fun getNonAdocFiles(directory: File): List<File> {
        if (!directory.exists() || !directory.isDirectory) {
            logger.warn("Source directory does not exist: ${directory.absolutePath}")
            return emptyList()
        }

        return directory.walkTopDown()
            .filter { it.isFile && (it.extension != "adoc" || it.extension == "md")}
            .toList()
    }

    /**
     * Converts all .adoc files in the source directory to HTML in the target directory
     * and copies all other files as-is.
     *
     * @param sourceDir The directory containing files to process
     * @param toDir The directory where files will be written
     * @return Statistics about the conversion process
     */
    fun convert(sourceDir: File, toDir: String, cssTheme: String = "github-markdown-css.css"): ConversionStats {
        val stats = ConversionStats()
        val targetDir = File(toDir)

        // Check if source and target directories exist
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            logger.warn("Source directory does not exist: ${sourceDir.absolutePath}")
            return stats
        }

        if (!targetDir.exists()) {
            logger.info("Creating target directory: $toDir")
            if (!targetDir.mkdirs()) {
                logger.error("Failed to create target directory: $toDir")
                return stats
            }
        } else if (!targetDir.isDirectory) {
            logger.error("Target path exists but is not a directory: $toDir")
            return stats
        }

        // Get all files from the source directory
        val adocFiles = getAdocFiles(sourceDir)
        val nonAdocFiles = getNonAdocFiles(sourceDir)
        val allSourceFiles = adocFiles + nonAdocFiles

        // Clean up deleted files first
        cleanupDeletedFiles(allSourceFiles, targetDir, stats)

        // Build comprehensive dependency map including non-AsciiDoc files
        val (fileDependencies, reverseDependencies) = buildDependencyMap(adocFiles, allSourceFiles)

        // Set to track files that need conversion
        val filesToConvert = mutableSetOf<File>()

        // First pass: identify files that need conversion based on content changes using virtual threads
        val identificationExecutor = Executors.newVirtualThreadPerTaskExecutor()
        val filesToConvertSet = ConcurrentHashMap.newKeySet<File>()

        val identificationTasks = adocFiles.map { file ->
            identificationExecutor.submit {
                // Calculate relative path from source directory
                val relativePath = sourceDir.toPath().relativize(file.toPath())

                val targetFile = targetDir.toPath().resolve(relativePath).toFile()
                val targetHtmlFile = targetDir.toPath().resolve(
                    relativePath.resolveSibling(relativePath.fileName.toString().replace(".adoc", ".html"))
                ).toFile()

                // Create parent directories if they don't exist
                targetFile.parentFile?.mkdirs()

                if (shouldConvertFile(file, targetFile, targetHtmlFile, fileDependencies)) {
                    filesToConvertSet.add(file)
                    synchronized(stats) {
                        stats.filesNeedingConversion++
                    }
                }
            }
        }

        // Wait for all identification tasks to complete
        identificationTasks.forEach { it.get() }
        identificationExecutor.close()

        filesToConvert.addAll(filesToConvertSet)

        // Second pass: add parent files that need conversion due to changed includes using virtual threads
        val additionalFilesSet = ConcurrentHashMap.newKeySet<File>()
        val dependencyExecutor = Executors.newVirtualThreadPerTaskExecutor()

        val dependencyTasks = filesToConvert.map { changedFile ->
            dependencyExecutor.submit {
                // If this file is included by other files, those files also need conversion
                reverseDependencies[changedFile]?.forEach { parentFile ->
                    if (!filesToConvert.contains(parentFile) && !additionalFilesSet.contains(parentFile)) {
                        additionalFilesSet.add(parentFile)
                        synchronized(stats) {
                            stats.filesNeedingConversion++
                        }
                        logger.info("Adding ${parentFile.name} for conversion because included file ${changedFile.name} has changed")
                    }
                }
            }
        }

        // Wait for all dependency tasks to complete
        dependencyTasks.forEach { it.get() }
        dependencyExecutor.close()

        filesToConvert.addAll(additionalFilesSet)

        // Convert all files that need conversion using virtual threads
        var executor = Executors.newVirtualThreadPerTaskExecutor()
        val tasks = filesToConvert.map { file ->
            executor.submit {
                // Calculate relative path from source directory
                val relativePath = sourceDir.toPath().relativize(file.toPath())

                val targetFile = targetDir.toPath().resolve(relativePath).toFile()
                val targetHtmlFile = targetDir.toPath().resolve(
                    relativePath.resolveSibling(relativePath.fileName.toString().replace(".adoc", ".html"))
                ).toFile()

                try {
                    // Create parent directories if they don't exist
                    targetFile.parentFile?.mkdirs()

                    val options = buildOptions(buildAttributes())
                    options.setMkDirs(true)
                    options.setBaseDir(file.parentFile.absolutePath)

                    // Set the output directory to the parent directory of the target file
                    // to preserve the directory structure
                    options.setToDir(targetFile.parentFile.absolutePath)

                    asciidoctor.convertFile(file, options)

                    // Copy the source .adoc file to the target directory
                    Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

                    synchronized(stats) {
                        stats.filesConverted++
                    }
                    logger.info("Successfully converted file: $relativePath")
                } catch (e: Exception) {
                    synchronized(stats) {
                        stats.filesFailed++
                        stats.failedFiles.add(relativePath.toString())
                    }
                    logger.error("Failed to convert file: $relativePath", e)
                }
            }
        }

        // Wait for all tasks to complete
        tasks.forEach { it.get() }
        executor.close()

        // Process Markdown files
        val mdFiles = sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .toList()
        var mdConverted = 0
        var mdFailed = 0
        executor = Executors.newVirtualThreadPerTaskExecutor()
        val mdTasks = mdFiles.map { file ->
            executor.submit {
                val relativePath = file.relativeTo(sourceDir).parent ?: ""
                val outputSubDir = if (relativePath.isNotEmpty()) {
                    "${targetDir.absolutePath}/$relativePath"
                } else {
                    targetDir.absolutePath
                }

                if (markdownConverter.convertMarkdownToHtml(file, outputSubDir, cssTheme= cssTheme)) {
                    mdConverted++
                } else {
                    mdFailed++
                }
            }
        }
        mdTasks.forEach { it.get() }
        executor.close()
        stats.filesConverted += mdConverted
        stats.filesFailed += mdFailed
        // Copy non-AsciiDoc files to the target directory
        copyNonAdocFiles(nonAdocFiles, toDir, stats)

        logger.info("Conversion stats: ${stats.filesNeedingConversion} files needed conversion, " +
                "${stats.filesConverted} files converted, ${stats.filesCopied} files copied, " +
                "${stats.filesFailed} files failed, ${stats.filesDeleted} files deleted")
        return stats
    }
    /**
     * Copies non-AsciiDoc files from source to target directory using virtual threads.
     * Only copies files that don't exist in the target directory or have different content.
     *
     * @param files List of non-AsciiDoc files to copy
     * @param toDir Target directory
     * @param stats ConversionStats to update with copy information
     */
    private fun copyNonAdocFiles(files: List<File>, toDir: String, stats: ConversionStats) {
        val targetDir = File(toDir)

        // Create target directory if it doesn't exist
        if (!targetDir.exists()) {
            logger.info("Creating target directory: $toDir")
            if (!targetDir.mkdirs()) {
                logger.error("Failed to create target directory: $toDir")
                return
            }
        } else if (!targetDir.isDirectory) {
            logger.error("Target path exists but is not a directory: $toDir")
            return
        }

        // Get the source directory from the first file to calculate relative paths
        val sourceDir = files.firstOrNull()?.let { firstFile ->
            // Find the common root directory by walking up the directory tree
            var currentDir = firstFile.parentFile
            while (currentDir != null && !files.all { it.absolutePath.startsWith(currentDir.absolutePath) }) {
                currentDir = currentDir.parentFile
            }
            currentDir
        }

        if (sourceDir == null) {
            logger.warn("Could not determine source directory for copying non-AsciiDoc files")
            return
        }

        // Use virtual threads for parallel file copying
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        val tasks = files.map { file ->
            executor.submit {
                try {
                    // Calculate relative path from source directory
                    val relativePath = sourceDir.toPath().relativize(file.toPath())
                    val targetFile = targetDir.toPath().resolve(relativePath).toFile()

                    // Create parent directories if they don't exist
                    targetFile.parentFile?.let { parentDir ->
                        if (!parentDir.exists() && !parentDir.mkdirs()) {
                            logger.error("Failed to create parent directory: ${parentDir.absolutePath}")
                            return@submit
                        }
                    }

                    // Check if the file needs to be copied (doesn't exist or content differs)
                    val needsCopy = !targetFile.exists() ||
                            Files.mismatch(file.toPath(), targetFile.toPath()) != -1L

                    if (needsCopy) {
                        // Copy the file to the target directory preserving directory structure
                        Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        synchronized(stats) {
                            stats.filesCopied++
                            stats.copiedFiles.add(relativePath.toString())
                        }
                        logger.info("Copied file: $relativePath")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to copy file: ${file.name}", e)
                }
            }
        }

        // Wait for all tasks to complete
        tasks.forEach { it.get() }
        executor.close()

        if (stats.filesCopied > 0) {
            logger.info("Copied ${stats.filesCopied} non-AsciiDoc files with directory structure preserved")
        }
    }

    private fun copyMarkdownThemes(targetDir: String) {
        val cssDir = File(targetDir, "css")
        cssDir.mkdirs()

        listOf("md-light.css", "md-dark.css").forEach { theme ->
            javaClass.getResourceAsStream("/themes/$theme")?.use { input ->
                File(cssDir, theme).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
    /**
     * Cleans up files in the target directory that no longer exist in the source using virtual threads.
     * This includes both .adoc files and their corresponding .html files,
     * as well as any other files that were copied from source to target.
     * Handles directory structure preservation.
     * Also removes target directories that don't have corresponding source directories.
     *
     * @param sourceFiles List of source files
     * @param targetDir Target directory
     * @param stats ConversionStats to update with deletion information
     */
    private fun cleanupDeletedFiles(sourceFiles: List<File>, targetDir: File, stats: ConversionStats) {
        // Get the source directory from the first source file (if available)
        val sourceDir = sourceFiles.firstOrNull()?.let { firstFile ->
            var currentDir = firstFile.parentFile
            while (currentDir != null && !sourceFiles.all { it.absolutePath.startsWith(currentDir.absolutePath) }) {
                currentDir = currentDir.parentFile
            }
            currentDir
        } ?: return

        // Get all source files (including non-AsciiDoc files)
        val nonAdocFiles = getNonAdocFiles(sourceDir)
        val allSourceFiles = sourceFiles + nonAdocFiles

        // Create a set of relative paths for efficient lookup
        val sourceRelativePaths = allSourceFiles.map { file ->
            sourceDir.toPath().relativize(file.toPath()).toString()
        }.toSet()

        // Create a set of source directory paths for efficient lookup
        val sourceDirectoryPaths = mutableSetOf<String>()
        allSourceFiles.forEach { file ->
            var parent = file.parentFile
            while (parent != null && parent.absolutePath.startsWith(sourceDir.absolutePath)) {
                sourceDirectoryPaths.add(sourceDir.toPath().relativize(parent.toPath()).toString())
                parent = parent.parentFile
            }
        }

        // Get all files in the target directory recursively
        val targetFiles = if (targetDir.exists()) {
            targetDir.walkTopDown()
                .filter { it.isFile }
                .toList()
        } else {
            emptyList()
        }

        // Filter files that need to be deleted
        val filesToDelete = targetFiles.filter { targetFile ->
            val relativePath = targetDir.toPath().relativize(targetFile.toPath()).toString()

            // Don't delete if:
            // 1. The file exists in source
            // 2. It's an HTML file with a corresponding .adoc file in source
            // 3. It's a sitemap file (sitemap-icon.svg or sitemap.html)
            val hasSourceFile = sourceRelativePaths.contains(relativePath)
            val isHtmlWithAdocSource = targetFile.extension == "html" &&
                    sourceRelativePaths.contains(relativePath.replace(".html", ".adoc"))
            val isSitemapFile = relativePath == "sitemap-icon.svg" || relativePath == "sitemap.html"

            !hasSourceFile && !isHtmlWithAdocSource && !isSitemapFile
        }

        // Use virtual threads for parallel file deletion
        if (filesToDelete.isNotEmpty()) {
            val executor = Executors.newVirtualThreadPerTaskExecutor()
            val tasks = filesToDelete.map { targetFile ->
                executor.submit {
                    try {
                        val relativePath = targetDir.toPath().relativize(targetFile.toPath()).toString()
                        if (targetFile.delete()) {
                            synchronized(stats) {
                                stats.filesDeleted++
                                stats.deletedFiles.add(relativePath)
                            }
                            logger.info("Deleted file: $relativePath")
                        } else {
                            logger.warn("Failed to delete file: $relativePath")
                        }
                    } catch (e: Exception) {
                        logger.error("Error deleting file: ${targetFile.name}", e)
                    }
                }
            }

            // Wait for all tasks to complete
            tasks.forEach { it.get() }
            executor.close()

            if (stats.filesDeleted > 0) {
                logger.info("Cleaned up ${stats.filesDeleted} deleted files")
            }
        }

        // Get all directories in the target directory
        val targetDirectories = if (targetDir.exists()) {
            targetDir.walkTopDown()
                .filter { it.isDirectory && it != targetDir }
                .toList()
        } else {
            emptyList()
        }

        // Filter directories that need to be deleted (those that don't have corresponding source directories)
        val directoriesToDelete = targetDirectories.filter { targetDirectory ->
            val relativePath = targetDir.toPath().relativize(targetDirectory.toPath()).toString()
            !sourceDirectoryPaths.contains(relativePath)
        }

        // Sort directories by depth (deepest first) to ensure we delete child directories before parent directories
        val sortedDirectoriesToDelete = directoriesToDelete.sortedByDescending { 
            it.absolutePath.count { c -> c == File.separatorChar } 
        }

        // Delete directories
        if (sortedDirectoriesToDelete.isNotEmpty()) {
            val directoriesDeleted = AtomicInteger(0)
            val executor = Executors.newVirtualThreadPerTaskExecutor()
            val tasks = sortedDirectoriesToDelete.map { directory ->
                executor.submit {
                    try {
                        val relativePath = targetDir.toPath().relativize(directory.toPath()).toString()
                        if (directory.delete()) {
                            synchronized(stats) {
                                stats.filesDeleted++
                                stats.deletedFiles.add("directory: $relativePath")
                            }
                            directoriesDeleted.incrementAndGet()
                            logger.info("Deleted directory: $relativePath")
                        } else {
                            logger.warn("Failed to delete directory: $relativePath")
                        }
                    } catch (e: Exception) {
                        logger.error("Error deleting directory: ${directory.name}", e)
                    }
                }
            }

            // Wait for all tasks to complete
            tasks.forEach { it.get() }
            executor.close()

            if (directoriesDeleted.get() > 0) {
                logger.info("Cleaned up ${directoriesDeleted.get()} deleted directories")
            }
        }
    }

    private fun buildEpubOptions(attrs: Attributes): Options {
        return Options.builder()
            .backend("epub3")
            .attributes(attrs)
            .safe(SafeMode.UNSAFE)
            .build()
    }

    /**
     * Asynchronously converts a list of files to EPUB format.
     *
     * @param files The list of AsciiDoc files to convert
     * @param toDir The directory where the EPUBs will be written
     * @return A CompletableFuture that will complete when all conversions are done
     */
    @Async
    fun convertToEpubAsync(files: List<File>, toDir: String): CompletableFuture<ConversionStats> {
        val stats = ConversionStats()
        val future = CompletableFuture<ConversionStats>()

        try {
            val maxConcurrentConversions = 2
            val executor = Executors.newFixedThreadPool(maxConcurrentConversions)

            val futures = files.map { file ->
                CompletableFuture.supplyAsync({
                    try {
                        val targetDir = File(toDir)
                        if (!targetDir.exists()) {
                            targetDir.mkdirs()
                        }

                        val epubFileName = file.nameWithoutExtension + ".epub"
                        val targetFile = File(targetDir, epubFileName)
                        targetFile.parentFile?.mkdirs()

                        val options = buildEpubOptions(buildEpubAttributes())
                        options.setMkDirs(true)
                        options.setToDir(targetFile.parentFile.absolutePath)

                        // Convert the file with EPUB3 backend
                        val asciidoctor = Asciidoctor.Factory.create()
                        asciidoctor.convertFile(file, options)

                        synchronized(stats) {
                            stats.filesConverted++
                        }
                        logger.info("Successfully converted file to EPUB: ${file.name}")
                        true
                    } catch (e: Exception) {
                        synchronized(stats) {
                            stats.filesFailed++
                            stats.failedFiles.add(file.name)
                        }
                        logger.error("Failed to convert file to EPUB: ${file.name}", e)
                        false
                    }
                }, executor)
            }

            CompletableFuture.allOf(*futures.toTypedArray())
                .orTimeout(30, TimeUnit.MINUTES)
                .whenComplete { _, throwable ->
                    executor.shutdown()
                    if (throwable != null) {
                        logger.error("EPUB conversion failed", throwable)
                        future.completeExceptionally(throwable)
                    } else {
                        logger.info("EPUB conversion completed: ${stats.filesConverted} files converted, ${stats.filesFailed} files failed")
                        future.complete(stats)
                    }
                }
        } catch (e: Exception) {
            logger.error("EPUB conversion failed", e)
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Converts a single AsciiDoc file to EPUB format synchronously.
     *
     * @param sourceFile The AsciiDoc file to convert
     * @param outputDir The directory where the EPUB will be written
     * @return ConversionStats with the result of the conversion
     */
    fun convertSingleFileToEpub(sourceFile: File, outputDir: String): ConversionStats {
        val stats = ConversionStats()

        if (!sourceFile.exists() || !sourceFile.isFile || sourceFile.extension != "adoc") {
            stats.filesFailed++
            stats.failedFiles.add("Invalid source file: ${sourceFile.name}")
            logger.error("Invalid source file for EPUB conversion: ${sourceFile.absolutePath}")
            return stats
        }

        try {
            val targetDir = File(outputDir)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            // Calculate the output EPUB file path
            val epubFileName = sourceFile.nameWithoutExtension + ".epub"
            val targetFile = File(targetDir, epubFileName)

            // Create parent directories if they don't exist
            targetFile.parentFile?.mkdirs()

            val options = buildEpubOptions(buildEpubAttributes())
            options.setMkDirs(true)
            options.setToDir(targetFile.parentFile.absolutePath)

            // Convert the file
            val asciidoctor = Asciidoctor.Factory.create()
            asciidoctor.requireLibrary("asciidoctor-diagram")
            asciidoctor.javaExtensionRegistry().docinfoProcessor(BlockSwitchDocinfoProcessor::class.java)
            asciidoctor.javaExtensionRegistry().docinfoProcessor(readingTimeDocinfoProcessor)
            asciidoctor.javaExtensionRegistry().docinfoProcessor(copyToClipboardDocinfoProcessor)
            asciidoctor.javaExtensionRegistry().docinfoProcessor(DocOpsMermaidDocinfoProcessor::class.java)
            asciidoctor.javaExtensionRegistry().docinfoProcessor(MermaidIncludeDocinfoProcessor::class.java)

            asciidoctor.rubyExtensionRegistry().loadClass(AsciiDoctorConverter::class.java.getResourceAsStream("/lib/docops-extension.rb"))
            asciidoctor.rubyExtensionRegistry().loadClass(AsciiDoctorConverter::class.java.getResourceAsStream("/lib/reactions_block_processor.rb"))

            asciidoctor.requireLibrary("asciidoctor-epub3")
            asciidoctor.convertFile(sourceFile, options)

            stats.filesConverted++
            logger.info("Successfully converted single file to EPUB: ${sourceFile.name} -> ${epubFileName}")

        } catch (e: Exception) {
            stats.filesFailed++
            stats.failedFiles.add(sourceFile.name)
            logger.error("Failed to convert single file to EPUB: ${sourceFile.name}", e)
        }

        return stats
    }

    /**
     * Asynchronously converts a single AsciiDoc file to EPUB format.
     *
     * @param sourceFile The AsciiDoc file to convert
     * @param outputDir The directory where the EPUB will be written
     * @return A CompletableFuture that will complete when the conversion is done
     */
    @Async
    fun convertSingleFileToEpubAsync(sourceFile: File, outputDir: String): CompletableFuture<ConversionStats> {
        val future = CompletableFuture<ConversionStats>()

        try {
            val stats = convertSingleFileToEpub(sourceFile, outputDir)
            future.complete(stats)
        } catch (e: Exception) {
            logger.error("Async EPUB conversion failed for single file: ${sourceFile.name}", e)
            future.completeExceptionally(e)
        }

        return future
    }
    private fun buildEpubAttributes(): Attributes {
        val docinfoDir = this::class.java.classLoader.getResource("docinfo")?.path
            ?: "src/main/resources/docinfo"

        return Attributes.builder()
            .sourceHighlighter("highlightjs")
            .allowUriRead(true)
            .linkAttrs(true)
            .attribute("local-debug", converterSettings.localDebug.toString())
            .attribute("panel-server", converterSettings.panelServer)
            .attribute("panel-webserver", converterSettings.panelWebserver)
            .attribute("ebook-format", "epub3")
            .attribute("ebook-validate", converterSettings.epubSettings?.validateOutput ?: true)
            .dataUri(false) // EPUB should embed resources differently
            .attribute("docinfodir", docinfoDir)
            .attribute("docinfo", "shared")
            // EPUB-specific metadata attributes
            .attribute("epub-chapter-level", 1)
            .attribute("toc-level", converterSettings.epubSettings?.tocLevel ?: 2)
            .apply {
                // Add optional EPUB metadata if configured
                converterSettings.epubSettings?.let { epubSettings ->
                    epubSettings.title?.let { attribute("epub-title-override", it) }
                    if (epubSettings.authors.isNotEmpty()) {
                        attribute("author", epubSettings.authors.joinToString(", "))
                    }
                    attribute("lang", epubSettings.language)
                    epubSettings.coverImagePath?.let {
                        if (File(it).exists()) {
                            attribute("front-cover-image", it)
                        }
                    }
                }
            }
            .build()
    }
}
