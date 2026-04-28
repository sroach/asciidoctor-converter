package gy.roach.asciidoctor.service

import gy.roach.asciidoctor.config.ConverterSettings
import gy.roach.asciidoctor.extension.CopyToClipboardDocinfoProcessor
import gy.roach.asciidoctor.extension.ReadingTimeDocinfoProcessor
import gy.roach.asciidoctor.service.ReadingTimeService
import gy.roach.asciidoctor.service.AsciiDoctorConverter
import gy.roach.asciidoctor.service.AsciiDoctorToWiki
import gy.roach.asciidoctor.service.MarkdownConverter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.jupiter.MockitoExtension
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod

@ExtendWith(MockitoExtension::class)
class AsciiDoctorConverterDeepIncludesTest {

    @Mock
    lateinit var markdownConverter: MarkdownConverter

    @Mock
    lateinit var asciiDoctorToWiki: AsciiDoctorToWiki

    lateinit var converterSettings: ConverterSettings

    lateinit var converter: AsciiDoctorConverter

    private lateinit var readingTimeDocinfoProcessor: ReadingTimeDocinfoProcessor

    private lateinit var copyToClipboardDocinfoProcessor: CopyToClipboardDocinfoProcessor

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        converterSettings = ConverterSettings()
        val readingTimeService = gy.roach.asciidoctor.service.ReadingTimeService()
        readingTimeDocinfoProcessor = ReadingTimeDocinfoProcessor(readingTimeService)
        copyToClipboardDocinfoProcessor = CopyToClipboardDocinfoProcessor()
        converter = AsciiDoctorConverter(
            converterSettings,
            readingTimeDocinfoProcessor,
            copyToClipboardDocinfoProcessor,
            markdownConverter,
            asciiDoctorToWiki
        )
    }

    @Test
    fun `extractIncludes handles deep chain with depth limit`() {
        val sourceDir = Files.createDirectories(tempDir.resolve("source")).toFile()

        // Create linear chain: level0 -> level1 -> ... -> level60 (61 files, max recursion depth 60 >50)
        val numLevels = 61
        for (i in 0 until numLevels) {
            val file = File(sourceDir, "level${i}.adoc")
            val content = """
                = Level $i
                Content at level $i
            """.trimIndent() + if (i < numLevels - 1) """
                include::level${i+1}.adoc[]
            """.trimIndent() else ""
            file.writeText(content)
        }

        val rootFile = File(sourceDir, "level0.adoc")
        val extractMethod = converter::class.memberFunctions.single { it.name == "extractIncludes" }
        val javaMethod = extractMethod.javaMethod!!
        javaMethod.isAccessible = true

        val visited = mutableSetOf<File>()
        @Suppress("UNCHECKED_CAST")
        val includes: Set<File> = javaMethod.invoke(converter, rootFile, visited, 0, 50) as Set<File>

        // With maxDepth=50, should collect levels 1 to 51 (51 files)
        assertEquals(51, includes.size)
        // Verify level1 and level51 included, level52 not
        assertTrue(includes.contains(File(sourceDir, "level1.adoc")))
        assertTrue(includes.contains(File(sourceDir, "level51.adoc")))
        assertFalse(includes.contains(File(sourceDir, "level52.adoc")))
    }

    @Test
    fun `extractIncludes detects cycles using visited set`() {
        val sourceDir = Files.createDirectories(tempDir.resolve("source")).toFile()

        val aFile = File(sourceDir, "a.adoc")
        aFile.writeText("""
            = A
            include::b.adoc[]
        """.trimIndent())

        val bFile = File(sourceDir, "b.adoc")
        bFile.writeText("""
            = B
            include::a.adoc[]
        """.trimIndent())

        val extractMethod = converter::class.memberFunctions.single { it.name == "extractIncludes" }
        val javaMethod = extractMethod.javaMethod!!
        javaMethod.isAccessible = true

        val visited = mutableSetOf<File>()
        @Suppress("UNCHECKED_CAST")
        val includes: Set<File> = javaMethod.invoke(converter, aFile, visited, 0, 50) as Set<File>

        assertEquals(2, includes.size)
        assertTrue(includes.contains(bFile))
        assertTrue(includes.contains(aFile))
        // In mutual cycle, both files are collected (direct + transitive)
    }
}