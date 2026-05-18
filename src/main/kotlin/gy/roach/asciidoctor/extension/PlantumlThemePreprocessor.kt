package gy.roach.asciidoctor.extension

import org.asciidoctor.ast.Document
import org.asciidoctor.extension.Preprocessor
import org.asciidoctor.extension.PreprocessorReader
import org.asciidoctor.extension.Reader
import org.slf4j.LoggerFactory

/**
 * Preprocessor that injects PlantUML light/dark theme skinparams into [plantuml] blocks.
 * Theme selection is driven by the "plantuml-theme" document attribute ("dark" or "light").
 * This provides consistent iOS-styled theming for AsciiDoc (matching the Markdown PlantumlNodeRenderer).
 *
 * Note: This is a custom implementation (not a built-in AsciiDoc/PlantUML feature).
 * We use skinparams instead of PlantUML's `!theme` directive because custom themes
 * are not resolved via `!theme dark`.
 */
class PlantumlThemePreprocessor : Preprocessor() {

    private val logger = LoggerFactory.getLogger(PlantumlThemePreprocessor::class.java)

    override fun process(document: Document, reader: PreprocessorReader): Reader {
        val themeAttr = document.attributes["docops-plantuml-theme"]?.toString()?.lowercase()?.trim()
        val useDark = themeAttr == "dark"
        val themePath = if (useDark) "/themes/plantuml-dark.puml" else "/themes/plantuml-light.puml"
        val themeContent = javaClass.getResourceAsStream(themePath)?.bufferedReader()?.use { it.readText() } ?: ""

        if (themeContent.isBlank()) {
            logger.warn("PlantumlThemePreprocessor: Theme file not found or empty at $themePath")
            return reader
        }

        val lines = reader.readLines()
        val processed = mutableListOf<String>()
        var insidePlantumlBlock = false
        var themeInjected = false

        for (line in lines) {
            val trimmed = line.trim()

            // Detect start of plantuml block
            if (!insidePlantumlBlock && (trimmed.startsWith("[plantuml") || trimmed == "plantuml::")) {
                insidePlantumlBlock = true
                themeInjected = false
            }

            // Detect delimiter start for literal block following [plantuml]
            if (!insidePlantumlBlock && trimmed == "----" && processed.lastOrNull()?.trim()?.startsWith("[plantuml") == true) {
                insidePlantumlBlock = true
                themeInjected = false
            }

            if (insidePlantumlBlock && trimmed == "@startuml" && !themeInjected) {
                processed.add(line)
                // Inject our custom skinparam theme immediately after @startuml
                // Skip any existing !theme lines that may have been added by asciidoctor-diagram
                themeContent.lines().forEach { themeLine ->
                    if (themeLine.isNotBlank()) {
                        processed.add(themeLine)
                    }
                }
                themeInjected = true
                continue
            }

            // Skip pre-existing !theme directives inside plantuml blocks to avoid "Cannot load theme" errors
            if (insidePlantumlBlock && trimmed.startsWith("!theme ")) {
                continue
            }

            // End of plantuml block
            if (insidePlantumlBlock && (trimmed == "@enduml" || trimmed == "----")) {
                insidePlantumlBlock = false
            }

            processed.add(line)
        }

        // Use processed list (main loop handles injection reliably)
        reader.restoreLines(processed)
        return reader
    }
}