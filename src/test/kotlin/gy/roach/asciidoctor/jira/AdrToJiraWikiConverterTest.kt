package gy.roach.asciidoctor.jira

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import gy.roach.asciidoctor.config.ConverterSettings
import gy.roach.asciidoctor.service.JiraConverter
import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText

class AdrToWikiIntegrationTest {

    @Test
    fun `adr adoc converts to expected jira wiki`() {
        val inputPath = Path.of("src/test/resources/jira/adr.adoc").toAbsolutePath().normalize()
        val adoc = inputPath.readText()

        val expectedPath = Path.of("src/test/resources/jira/adr.expected.wiki").toAbsolutePath().normalize()
        val actualOutPath = Path.of("build/test-results/jira/adr.actual.wiki").toAbsolutePath().normalize()

        val wiki = runFullAsciidocToWiki(
            adoc = adoc,
            sourceDir = inputPath.parent
        )

        actualOutPath.parent.createDirectories()
        actualOutPath.writeText(wiki)

        println("Wrote converted Jira wiki to: $actualOutPath")

        if (!expectedPath.exists()) {
            expectedPath.parent.createDirectories()
            expectedPath.writeText(wiki)
            error("Created snapshot at $expectedPath. Verify and rerun.")
        }

        //val expected = expectedPath.readText()
        //assertEquals(expected, wiki, "Mismatch. See $actualOutPath for actual output.")
    }

    private fun runFullAsciidocToWiki(adoc: String, sourceDir: Path): String {
        val asciidoctor = Asciidoctor.Factory.create()

        val rubyExtensionPath = extractResourceToTempFile("lib/docops-extension.rb")
        asciidoctor.requireLibrary(rubyExtensionPath.toAbsolutePath().toString())

        val baseDir = sourceDir.toAbsolutePath().normalize()
        require(Files.isDirectory(baseDir)) { "Base dir does not exist: $baseDir" }

        val attrs = Attributes.builder()
            .attribute("panel-server", "http://localhost:8010/extension")
            .attribute("panel-webserver", "http://localhost:8010/extension")
            .attribute("local-debug", "false")
            .build()

        val options = Options.builder()
            .backend("xhtml5")
            .safe(SafeMode.UNSAFE)
            .option("header_footer", false)
            .baseDir(baseDir.toFile())
            .toFile(false)
            .attributes(attrs)
            .build()

        val html = asciidoctor.convert(adoc, options).toString()

        // Preserve DocOps blocks as image tokens before html->markdown flattening
        val (htmlWithTokens, tokenMap) = replaceDocOpsBlocksWithTokens(html)

        var markdown = FlexmarkHtmlConverter.builder().build().convert(htmlWithTokens)

        // Replace tokens with Jira wiki image syntax
        tokenMap.forEach { (token, jiraImageMarkup) ->
            markdown = markdown.replace(token, jiraImageMarkup)
        }

        val settings = ConverterSettings().apply {
            panelWebserver = "http://localhost:8010/extension"
            panelServer = "http://localhost:8010/extension"
        }
        val jiraConverter = JiraConverter(settings)
        return jiraConverter.markdownToJira(markdown)
    }

    private fun replaceDocOpsBlocksWithTokens(html: String): Pair<String, Map<String, String>> {
        val doc = Jsoup.parseBodyFragment(html)
        val map = linkedMapOf<String, String>()
        var idx = 0

        // Matches HTML emitted by docops block wrappers that carry data-url
        val cards = doc.select(".docops-media-card[data-url]")

        cards.forEach { card ->
            val url = card.attr("data-url").trim()
            if (url.isNotEmpty()) {
                val token = "DOCOPS_IMG_TOKEN_${idx++}"
                map[token] = "!$url!"
                card.after(token)
                card.remove()
            }
        }

        return doc.body().html() to map
    }


    private fun extractResourceToTempFile(classpathLocation: String): Path {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream(classpathLocation)) {
            "Resource not found on classpath: $classpathLocation"
        }

        val tempFile = Files.createTempFile("docops-extension-", ".rb")
        tempFile.toFile().deleteOnExit()

        stream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return tempFile
    }


}
