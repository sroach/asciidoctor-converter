package gy.roach.asciidoctor.service

import gy.roach.asciidoctor.config.ConverterSettings
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownConverterTest {

    @Test
    fun `should include favicon when food_blog_styles css is used`() {
        val converterSettings = ConverterSettings()
        val markdownContent = "# Hello World"
        val title = "Test Title"
        val cssTheme = "food_blog_styles.css"

        val html = MermaidFlexmark.createFullHtmlWithMermaid(markdownContent, converterSettings, title, cssTheme)

        assertTrue(html.contains("<link rel=\"icon\" type=\"image/svg+xml\""), "Favicon link should be present")
        assertTrue(html.contains("data:image/svg+xml;base64,"), "Favicon should be base64 encoded")
    }

    @Test
    fun `should NOT include favicon when default css is used`() {
        val converterSettings = ConverterSettings()
        val markdownContent = "# Hello World"
        val title = "Test Title"
        val cssTheme = "github-markdown-css.css"

        val html = MermaidFlexmark.createFullHtmlWithMermaid(markdownContent, converterSettings, title, cssTheme)

        assertFalse(html.contains("<link rel=\"icon\" type=\"image/svg+xml\""), "Favicon link should NOT be present")
    }
}
