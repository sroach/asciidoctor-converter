package gy.roach.asciidoctor.service

import gy.roach.asciidoctor.config.ConverterSettings
import gy.roach.asciidoctor.extension.CopyToClipboardDocinfoProcessor
import gy.roach.asciidoctor.extension.ReadingTimeDocinfoProcessor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.jupiter.MockitoExtension
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod

@ExtendWith(MockitoExtension::class)
class AsciiDoctorConverterNestedIncludeAttributeTest {

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
        val readingTimeService = ReadingTimeService()
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
    fun `extractIncludes resolves attributes from parent in nested includes`() {
        val sourceDir = Files.createDirectories(tempDir.resolve("source")).toFile()

        val mainFile = File(sourceDir, "main.adoc")
        mainFile.writeText("""
            :version: 1.0
            include::sub.adoc[]
        """.trimIndent())

        val subFile = File(sourceDir, "sub.adoc")
        subFile.writeText("""
            include::policy-{version}.adoc[]
        """.trimIndent())

        val policyFile = File(sourceDir, "policy-1.0.adoc")
        policyFile.writeText("Policy content")

        val extractMethod = converter::class.memberFunctions.single { it.name == "extractIncludes" }
        val javaMethod = extractMethod.javaMethod!!
        javaMethod.isAccessible = true

        val visited = mutableSetOf<File>()
        @Suppress("UNCHECKED_CAST")
        val includes: Set<File> = javaMethod.invoke(converter, mainFile, emptyMap<String, Any>(), visited, 0, 10) as Set<File>

        assertTrue(includes.contains(subFile), "Should include sub.adoc")
        assertTrue(includes.contains(policyFile), "Should include policy-1.0.adoc")
    }
}
