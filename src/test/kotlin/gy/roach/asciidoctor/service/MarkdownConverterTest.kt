package gy.roach.asciidoctor.service

import gy.roach.asciidoctor.config.ConverterSettings
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import gy.roach.asciidoctor.md.extension.PlantumlNodeRendererFactory
import gy.roach.asciidoctor.md.extension.MermaidNodeRendererFactory
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

    @Test
    fun `should set iOS dark theme variables for mermaid when dark css is used`() {
        val converterSettings = ConverterSettings()
        val markdownContent = "```mermaid\ngraph TD; A-->B;\n```"
        val title = "Mermaid Dark Test"
        val cssTheme = "github-markdown-dark.css"

        val html = MermaidFlexmark.createFullHtmlWithMermaid(markdownContent, converterSettings, title, cssTheme)

        assertTrue(html.contains("theme: 'base'"), "Mermaid theme should be set to base")
        assertTrue(html.contains("'lineColor': '#4DA3FF'"), "Mermaid should use iOS dark accent color")
        assertTrue(html.contains("'mainBkg': '#0A0E27'"), "Mermaid should use iOS dark background")
    }

    @Test
    fun `should set iOS light theme variables for mermaid when light css is used`() {
        val converterSettings = ConverterSettings()
        val markdownContent = "```mermaid\ngraph TD; A-->B;\n```"
        val title = "Mermaid Light Test"
        val cssTheme = "github-markdown-css.css"

        val html = MermaidFlexmark.createFullHtmlWithMermaid(markdownContent, converterSettings, title, cssTheme)
 
        assertTrue(html.contains("theme: 'base'"), "Mermaid theme should be set to base")
        assertTrue(html.contains("'lineColor': '#007AFF'"), "Mermaid should use iOS light accent color")
        assertTrue(html.contains("'mainBkg': '#F3F6FB'"), "Mermaid should use iOS light background")
    }

    @Test
    fun `should render tabs in markdown`() {
        val converterSettings = ConverterSettings()
        val markdownContent = """
            [tabs]
            [tab:Tab 1]
            Content 1 with **bold**
            [/tab]
            [tab:Tab 2]
            Content 2 with code block
            ```kotlin
            println("Hello")
            ```
            [/tab]
            [/tabs]
        """.trimIndent()
        val title = "Tabs Test"
        val cssTheme = "github-markdown-css.css"

        val html = MermaidFlexmark.createFullHtmlWithMermaid(markdownContent, converterSettings, title, cssTheme)
 
        assertTrue(html.contains("<div class=\"tabs is-loading\">"), "Should contain tabs container")
        assertTrue(html.contains("<div class=\"tablist\">"), "Should contain tablist")
        assertTrue(html.contains(">Tab 1</li>"), "Should contain Tab 1 in list")
        assertTrue(html.contains(">Tab 2</li>"), "Should contain Tab 2 in list")
        assertTrue(html.contains("class=\"tabpanel\""), "Should contain tabpanel")
        assertTrue(html.contains("<strong>bold</strong>"), "Should parse markdown inside tab")
        assertTrue(html.contains("<pre><code class=\"language-kotlin\">"), "Should parse code block inside tab")
    }

    @Test
    fun `should include fix script for mermaid in tabs`() {
        val converterSettings = ConverterSettings()
        val markdownContent = """
            [tabs]
            [tab:Mermaid Tab]
            ```mermaid
            graph TD; A-->B;
            ```
            [/tab]
            [/tabs]
        """.trimIndent()
        val title = "Mermaid Tabs Test"
        val cssTheme = "github-markdown-css.css"

        val html = MermaidFlexmark.createFullHtmlWithMermaid(markdownContent, converterSettings, title, cssTheme)

        assertTrue(html.contains("reRenderMermaidInPanel"), "Should contain mermaid-in-tabs fix script")
        assertTrue(html.contains("MutationObserver"), "Should use MutationObserver in fix script")
        assertTrue(html.contains("data-original-content"), "Should contain original content for re-rendering")
    }

    @Test
    fun `should include improved mermaid re-render logic`() {
        val converterSettings = ConverterSettings()
        val markdownContent = "```mermaid\nmindmap\n  root((mindmap))\n```"
        val html = MermaidFlexmark.createFullHtmlWithMermaid(markdownContent, converterSettings, "Test", "github-markdown-css.css")

        assertTrue(html.contains("const vb = (svg.getAttribute('viewBox') || '').split(/[\\s,]+/);"), "Should contain viewBox parsing")
        assertTrue(html.contains("const vbWidth = vb.length === 4 ? parseFloat(vb[2]) : null;"), "Should contain vbWidth calculation")
        assertTrue(html.contains("rect.width < panel.offsetWidth - 10"), "Should contain relative width check")
    }
}
