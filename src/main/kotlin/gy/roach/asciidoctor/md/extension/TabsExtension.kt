package gy.roach.asciidoctor.md.extension

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html.HtmlWriter
import com.vladsch.flexmark.html.renderer.NodeRenderer
import com.vladsch.flexmark.html.renderer.NodeRendererContext
import com.vladsch.flexmark.html.renderer.NodeRendererFactory
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.parser.block.*
import com.vladsch.flexmark.util.ast.Block
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.data.DataHolder
import com.vladsch.flexmark.util.data.MutableDataHolder
import com.vladsch.flexmark.util.sequence.BasedSequence
import java.util.regex.Pattern

/**
 * Custom block node for a set of tabs
 */
class TabsBlock : Block() {
    override fun getSegments(): Array<BasedSequence> = EMPTY_SEGMENTS
}

/**
 * Custom block node for a single tab panel
 */
class TabPanelBlock : Block() {
    var title: String = ""
    var tabId: String = ""
    var panelId: String = ""
    override fun getSegments(): Array<BasedSequence> = EMPTY_SEGMENTS
}

class TabsExtension private constructor() :
    Parser.ParserExtension,
    HtmlRenderer.HtmlRendererExtension {

    companion object {
        fun create() = TabsExtension()

        private val TABS_OPEN_PATTERN = Pattern.compile("""^\[tabs]\s*$""")
        private val TABS_CLOSE_PATTERN = Pattern.compile("""^\[/tabs]\s*$""")
        private val TAB_OPEN_PATTERN = Pattern.compile("""^\[tab:(.+)]\s*$""")
        private val TAB_CLOSE_PATTERN = Pattern.compile("""^\[/tab]\s*$""")
    }

    override fun extend(parserBuilder: Parser.Builder) {
        parserBuilder.customBlockParserFactory(TabsBlockParserFactory())
        parserBuilder.customBlockParserFactory(TabPanelBlockParserFactory())
    }

    override fun extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String) {
        htmlRendererBuilder.nodeRendererFactory(TabsNodeRenderer.Factory())
    }

    override fun parserOptions(options: MutableDataHolder) {}
    override fun rendererOptions(options: MutableDataHolder) {}

    /**
     * Factory for TabsBlock
     */
    class TabsBlockParserFactory : CustomBlockParserFactory {
        override fun getAfterDependents(): Set<Class<*>>? = null
        override fun getBeforeDependents(): Set<Class<*>>? = null
        override fun affectsGlobalScope(): Boolean = false
        override fun apply(options: DataHolder): BlockParserFactory = TabsBlockParserFactoryImpl(options)
    }

    class TabsBlockParserFactoryImpl(options: DataHolder) : AbstractBlockParserFactory(options) {
        override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
            val line = state.line
            if (TABS_OPEN_PATTERN.matcher(line).find()) {
                return BlockStart.of(TabsBlockParser()).atIndex(line.length)
            }
            return BlockStart.none()
        }
    }

    class TabsBlockParser : AbstractBlockParser() {
        private val block = TabsBlock()
        private var isFinished = false

        override fun getBlock(): Block = block

        override fun tryContinue(state: ParserState): BlockContinue? {
            if (isFinished) return BlockContinue.none()
            val line = state.line.toString().trim()
            if (TABS_CLOSE_PATTERN.matcher(line).find()) {
                isFinished = true
                return BlockContinue.atIndex(state.line.length)
            }
            return BlockContinue.atIndex(state.index)
        }

        override fun closeBlock(state: ParserState) {
            block.setCharsFromContent()
        }

        override fun isContainer(): Boolean = true
        override fun canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean = true
    }

    /**
     * Factory for TabPanelBlock
     */
    class TabPanelBlockParserFactory : CustomBlockParserFactory {
        override fun getAfterDependents(): Set<Class<*>>? = null
        override fun getBeforeDependents(): Set<Class<*>>? = null
        override fun affectsGlobalScope(): Boolean = false
        override fun apply(options: DataHolder): BlockParserFactory = TabPanelBlockParserFactoryImpl(options)
    }

    class TabPanelBlockParserFactoryImpl(options: DataHolder) : AbstractBlockParserFactory(options) {
        override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
            val line = state.line
            val matcher = TAB_OPEN_PATTERN.matcher(line)
            if (matcher.find()) {
                val title = matcher.group(1).trim()
                return BlockStart.of(TabPanelBlockParser(title)).atIndex(line.length)
            }
            return BlockStart.none()
        }
    }

    class TabPanelBlockParser(title: String) : AbstractBlockParser() {
        private val block = TabPanelBlock()
        private var isFinished = false

        init {
            block.title = title
            val timestamp = System.currentTimeMillis()
            val randomSuffix = (Math.random() * 1000).toInt()
            val baseId = title.lowercase().replace(Regex("[^a-z0-9]"), "-")
            block.tabId = "tab-$baseId-$timestamp-$randomSuffix"
            block.panelId = "panel-$baseId-$timestamp-$randomSuffix"
        }

        override fun getBlock(): Block = block

        override fun tryContinue(state: ParserState): BlockContinue? {
            if (isFinished) return BlockContinue.none()
            val line = state.line.toString().trim()
            if (TAB_CLOSE_PATTERN.matcher(line).find()) {
                isFinished = true
                return BlockContinue.atIndex(state.line.length)
            }
            return BlockContinue.atIndex(state.index)
        }

        override fun closeBlock(state: ParserState) {
            block.setCharsFromContent()
        }

        override fun isContainer(): Boolean = true
        override fun canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean = true
    }

    /**
     * Renderer for Tabs and Tab Panels
     */
    class TabsNodeRenderer : NodeRenderer {
        override fun getNodeRenderingHandlers(): Set<NodeRenderingHandler<*>> {
            return setOf(
                NodeRenderingHandler(TabsBlock::class.java, ::renderTabs),
                NodeRenderingHandler(TabPanelBlock::class.java, ::renderTabPanel)
            )
        }

        private fun renderTabs(node: TabsBlock, context: NodeRendererContext, html: HtmlWriter) {
            html.line()
            html.attr("class", "tabs is-loading")
            html.withAttr().tag("div")
            html.line()

            // First, collect all tabs to render the tablist
            val panels = mutableListOf<TabPanelBlock>()
            var child = node.firstChild
            while (child != null) {
                if (child is TabPanelBlock) {
                    panels.add(child)
                }
                child = child.next
            }

            // Render tablist
            html.attr("class", "tablist")
            html.withAttr().tag("div")
            html.attr("role", "tablist")
            html.withAttr().tag("ul")
            panels.forEachIndexed { index, panel ->
                html.attr("id", panel.tabId)
                html.attr("class", "tab")
                html.attr("role", "tab")
                html.attr("aria-controls", panel.panelId)
                html.attr("aria-selected", if (index == 0) "true" else "false")
                html.attr("tabindex", if (index == 0) "0" else "-1")
                html.withAttr().tag("li")
                html.text(panel.title)
                html.closeTag("li")
            }
            html.closeTag("ul")
            html.closeTag("div")
            html.line()

            // Render panels
            context.renderChildren(node)

            html.closeTag("div")
            html.line()
        }

        private fun renderTabPanel(node: TabPanelBlock, context: NodeRendererContext, html: HtmlWriter) {
            val index = node.parent!!.firstChild.let { first ->
                var i = 0
                var curr = first
                while (curr != node && curr != null) {
                    if (curr is TabPanelBlock) i++
                    curr = curr.next
                }
                i
            }
            html.attr("id", node.panelId)
            html.attr("class", if (index == 0) "tabpanel" else "tabpanel is-hidden")
            html.attr("aria-labelledby", node.tabId)
            html.attr("role", "tabpanel")
            if (index != 0) {
                html.attr("hidden", "")
            }
            html.withAttr().tag("div")
            context.renderChildren(node)
            html.closeTag("div")
            html.line()
        }

        class Factory : NodeRendererFactory {
            override fun apply(options: DataHolder): NodeRenderer = TabsNodeRenderer()
        }
    }
}
