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
        var activeDelimiter: String? = null

        for (line in lines) {
            val trimmed = line.trim()

            // 1. Detect start of plantuml block context
            if (!insidePlantumlBlock) {
                if (trimmed.startsWith("[plantuml") || trimmed.startsWith("plantuml::")) {
                    // Check if it's a one-liner macro with target (e.g., plantuml::diag.puml[])
                    if (trimmed.startsWith("plantuml::") && trimmed.contains("[") && !trimmed.startsWith("plantuml::[")) {
                        processed.add(line)
                        continue
                    }
                    insidePlantumlBlock = true
                    themeInjected = false
                    activeDelimiter = null
                    processed.add(line)
                    continue
                }
            }

            // 2. Handle block content and transitions if inside
            if (insidePlantumlBlock) {
                // Handle opening/closing delimiters (---- or ....)
                if (activeDelimiter == null && !themeInjected && (trimmed == "----" || trimmed == "....")) {
                    activeDelimiter = trimmed
                    processed.add(line)
                    continue
                }

                if (activeDelimiter != null && trimmed == activeDelimiter) {
                    insidePlantumlBlock = false
                    activeDelimiter = null
                    processed.add(line)
                    continue
                }

                // Handle theme injection at @startuml
                if (trimmed == "@startuml" && !themeInjected) {
                    processed.add(line)
                    themeContent.lines().filter { it.isNotBlank() }.forEach { themeLine ->
                        processed.add(themeLine)
                    }
                    themeInjected = true
                    continue
                }

                // Handle end of un-delimited block
                if (trimmed == "@enduml") {
                    if (activeDelimiter == null) {
                        insidePlantumlBlock = false
                    }
                    processed.add(line)
                    continue
                }

                // Skip pre-existing !theme directives
                if (trimmed.startsWith("!theme ")) {
                    continue
                }
            }

            processed.add(line)
        }

        // Use processed list (main loop handles injection reliably)
        reader.restoreLines(processed)
        return reader
    }
}