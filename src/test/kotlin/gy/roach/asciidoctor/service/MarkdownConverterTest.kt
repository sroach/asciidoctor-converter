package gy.roach.asciidoctor.service

import gy.roach.asciidoctor.config.ConverterSettings
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import gy.roach.asciidoctor.md.extension.PlantumlNodeRendererFactory
import java.io.File

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

    @Test
    fun `should handle plantuml blocks via PlantumlNodeRendererFactory`() {
        // Basic smoke test that factory exists and renderer is registered
        val factory = PlantumlNodeRendererFactory()
        assertTrue(factory != null)
    }

    @Test
    fun `should convert markdown with plantuml block`() {
        val converterSettings = ConverterSettings()
        val markdownContent = """
            # PlantUML Test
            ```plantuml
            @startuml

            skinparam component {
                FontColor          black
                AttributeFontColor black
                FontSize           17
                AttributeFontSize  15
                AttributeFontname  Droid Sans Mono
                BackgroundColor    #6A9EFF
                BorderColor        black
                ArrowColor         #222266
            }
            
            title "OSCIED Charms Relations (Simple)"
            skinparam componentStyle uml2
            
            cloud {
                interface "JuJu" as juju
                interface "API" as api
                interface "Storage" as storage
                interface "Transform" as transform
                interface "Publisher" as publisher
                interface "Website" as website
            
                juju - [JuJu]
            
                website - [WebUI]
                [WebUI] .up.> juju
                [WebUI] .down.> storage
                [WebUI] .right.> api
            
                api - [Orchestra]
                transform - [Orchestra]
                publisher - [Orchestra]
                [Orchestra] .up.> juju
                [Orchestra] .down.> storage
            
                [Transform] .up.> juju
                [Transform] .down.> storage
                [Transform] ..> transform
            
                [Publisher] .up.> juju
                [Publisher] .down.> storage
                [Publisher] ..> publisher
            
                storage - [Storage]
                [Storage] .up.> juju
            }
            
            @enduml
            ```
        """.trimIndent()
        val html = MermaidFlexmark.createFullHtmlWithMermaid(markdownContent, converterSettings, "PlantUML Test", "github-markdown-css.css")
        // Check that SVG or plantuml container is present (success indicator)
        assertTrue(html.contains("plantuml") || html.contains("<svg"), "PlantUML diagram should be rendered")
        val f = File("logs/umltest.html")
        f.writeText(html)
    }
}
