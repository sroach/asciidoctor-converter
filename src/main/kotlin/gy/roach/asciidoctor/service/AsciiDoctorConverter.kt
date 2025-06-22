package gy.roach.asciidoctor.service

import gy.roach.asciidoctor.config.ConverterSettings
import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import kotlin.concurrent.thread

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
class AsciiDoctorConverter(private val converterSettings: ConverterSettings) {
    val asciidoctor: Asciidoctor = Asciidoctor.Factory.create()
    private val logger = LoggerFactory.getLogger(AsciiDoctorConverter::class.java)

    // Pattern to match include directives in Asciidoctor files
    private val includePattern = Pattern.compile("include::([^\\[\\]]+)(?:\\[.*?\\])?")

    init {
        asciidoctor.requireLibrary("asciidoctor-diagram")
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
                // Recursively extract includes from the included file
                includes.addAll(extractIncludes(includedFile))
            } else {
                logger.warn("Included file not found: $includePath in ${file.name}")
            }
        }

        return includes
    }

    /**
     * Builds a dependency map between files and their includes.
     * This map is used to determine if a file needs to be converted
     * when one of its included files has changed.
     * 
     * @return Pair of maps: first is file dependencies, second is reverse dependencies
     */
    private fun buildDependencyMap(files: List<File>): Pair<Map<File, Set<File>>, Map<File, Set<File>>> {
        val fileDependencies = mutableMapOf<File, MutableSet<File>>()
        val reverseDependencies = mutableMapOf<File, MutableSet<File>>()

        // First, build direct dependencies
        files.forEach { file ->
            fileDependencies[file] = extractIncludes(file).toMutableSet()
        }

        // Then, build reverse dependencies (which files include this file)
        fileDependencies.forEach { (parent, includes) ->
            includes.forEach { include ->
                if (!reverseDependencies.containsKey(include)) {
                    reverseDependencies[include] = mutableSetOf()
                }
                reverseDependencies[include]?.add(parent)
            }
        }

        logger.info("Built dependency map for ${files.size} files, found ${fileDependencies.values.sumOf { it.size }} include relationships")
        return Pair(fileDependencies, reverseDependencies)
    }

    /**
     * Converts a list of .adoc files to HTML in the target directory and copies any non-AsciiDoc files
     * from the same source directory. Uses virtual threads for parallel processing.
     *
     * @param files List of .adoc files to convert
     * @param toDir The directory where converted files will be written
     * @return Statistics about the conversion process
     */
    fun convert(files: List<File>, toDir: String): ConversionStats {
        val stats = ConversionStats()
        val targetDir = File(toDir)

        // Create target directory if it doesn't exist
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

        // Get the source directory from the first file (if available)
        val sourceDir = files.firstOrNull()?.parentFile

        // Get non-AsciiDoc files if we have a source directory
        val nonAdocFiles = if (sourceDir != null) {
            getNonAdocFiles(sourceDir)
        } else {
            emptyList()
        }

        // Get all source files (both AsciiDoc and non-AsciiDoc)
        val allSourceFiles = files + nonAdocFiles

        // Clean up deleted files
        cleanupDeletedFiles(allSourceFiles, targetDir, stats)

        // Build dependency map for all files
        val (fileDependencies, reverseDependencies) = buildDependencyMap(files)

        // Set to track files that need conversion
        val filesToConvert = mutableSetOf<File>()

        // First pass: identify files that need conversion based on content changes using virtual threads
        val identificationExecutor = Executors.newVirtualThreadPerTaskExecutor()
        val filesToConvertSet = ConcurrentHashMap.newKeySet<File>()

        val identificationTasks = files.map { file ->
            identificationExecutor.submit {
                val targetFile = File(toDir, file.name)
                val targetHtmlFile = File(toDir, file.nameWithoutExtension + ".html")

                if (shouldConvertFile(file, targetFile, targetHtmlFile)) {
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
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        val tasks = filesToConvert.map { file ->
            executor.submit {
                val targetFile = File(toDir, file.name)
                try {
                    val options = buildOptions(buildAttributes())
                    options.setMkDirs(true)
                    options.setToDir(toDir)
                    asciidoctor.convertFile(file, options)

                    // Copy the source .adoc file to the target directory
                    Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

                    synchronized(stats) {
                        stats.filesConverted++
                    }
                    logger.info("Successfully converted file: ${file.name}")
                } catch (e: Exception) {
                    synchronized(stats) {
                        stats.filesFailed++
                        stats.failedFiles.add(file.name)
                    }
                    logger.error("Failed to convert file: ${file.name}", e)
                }
            }
        }

        // Wait for all tasks to complete
        tasks.forEach { it.get() }
        executor.close()

        // Copy non-AsciiDoc files to the target directory
        if (nonAdocFiles.isNotEmpty()) {
            copyNonAdocFiles(nonAdocFiles, toDir, stats)
        }

        logger.info("Conversion stats: ${stats.filesNeedingConversion} files needed conversion, " +
                "${stats.filesConverted} files converted, ${stats.filesCopied} files copied, " +
                "${stats.filesFailed} files failed, ${stats.filesDeleted} files deleted")
        return stats
    }

    /**
     * Determines if a file needs to be converted based on:
     * 1. If the target .adoc file doesn't exist
     * 2. If the target HTML file doesn't exist
     * 3. If the content of the source and target .adoc files differs
     */
    private fun shouldConvertFile(sourceFile: File, targetAdocFile: File, targetHtmlFile: File): Boolean {
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
        }
        return contentDiffers
    }

    private fun buildAttributes(): Attributes {
        // Get the docinfo directory from resources
        val docinfoDir = this::class.java.classLoader.getResource("docinfo")?.path
            ?: "src/main/resources/docinfo"

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
            .attribute("docinfodir", docinfoDir)
            .attribute("docinfo", "shared")
            .build()

    }
    private fun buildOptions(attrs: Attributes): Options {
        return  Options.builder()
            .backend("html")
            .attributes(attrs)
            .safe(SafeMode.UNSAFE)
            .build()
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
            .filter { it.isFile && it.extension != "adoc" }
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
    fun convert(sourceDir: File, toDir: String): ConversionStats {
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

        // Convert AsciiDoc files
        val (fileDependencies, reverseDependencies) = buildDependencyMap(adocFiles)

        // Set to track files that need conversion
        val filesToConvert = mutableSetOf<File>()

        // First pass: identify files that need conversion based on content changes using virtual threads
        val identificationExecutor = Executors.newVirtualThreadPerTaskExecutor()
        val filesToConvertSet = ConcurrentHashMap.newKeySet<File>()

        val identificationTasks = adocFiles.map { file ->
            identificationExecutor.submit {
                val targetFile = File(toDir, file.name)
                val targetHtmlFile = File(toDir, file.nameWithoutExtension + ".html")

                if (shouldConvertFile(file, targetFile, targetHtmlFile)) {
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
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        val tasks = filesToConvert.map { file ->
            executor.submit {
                val targetFile = File(toDir, file.name)
                try {
                    val options = buildOptions(buildAttributes())
                    options.setMkDirs(true)
                    options.setToDir(toDir)
                    asciidoctor.convertFile(file, options)

                    // Copy the source .adoc file to the target directory
                    Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

                    synchronized(stats) {
                        stats.filesConverted++
                    }
                    logger.info("Successfully converted file: ${file.name}")
                } catch (e: Exception) {
                    synchronized(stats) {
                        stats.filesFailed++
                        stats.failedFiles.add(file.name)
                    }
                    logger.error("Failed to convert file: ${file.name}", e)
                }
            }
        }

        // Wait for all tasks to complete
        tasks.forEach { it.get() }
        executor.close()

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
                        logger.info("Copied file: ${relativePath}")
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


    /**
     * Cleans up files in the target directory that no longer exist in the source using virtual threads.
     * This includes both .adoc files and their corresponding .html files,
     * as well as any other files that were copied from source to target.
     * Handles directory structure preservation.
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
            val hasSourceFile = sourceRelativePaths.contains(relativePath)
            val isHtmlWithAdocSource = targetFile.extension == "html" &&
                    sourceRelativePaths.contains(relativePath.replace(".html", ".adoc"))

            !hasSourceFile && !isHtmlWithAdocSource
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
    }
}
