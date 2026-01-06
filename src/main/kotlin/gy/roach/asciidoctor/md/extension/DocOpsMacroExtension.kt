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
import com.vladsch.flexmark.util.data.DataHolder
import com.vladsch.flexmark.util.data.DataKey
import com.vladsch.flexmark.util.data.MutableDataHolder
import com.vladsch.flexmark.util.sequence.BasedSequence
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*
import java.util.regex.Pattern
import java.util.zip.GZIPOutputStream


/**
 * Custom block node for DocOps macros with body content
 */
class DocOpsMacroBlock : Block() {
    var kind: String = ""
    var body: String = ""
    var options: Map<String, String> = emptyMap()

    override fun getSegments(): Array<BasedSequence> = EMPTY_SEGMENTS
}

/**
 * Extension for DocOps SVG macros that fetch SVG content from a server
 *
 * Syntax: [docops:kind options...]
 * body content (can include lists, multi-line text, etc.)
 * [/docops]
 *
 * Example:
 * [docops:adr scale=1.0 useDark=false]
 * title: My ADR
 * status: Accepted
 * context:
 * - Some context item
 * [/docops]
 */
class DocOpsMacroExtension private constructor() :
    Parser.ParserExtension,
    HtmlRenderer.HtmlRendererExtension {

    companion object {
        // Configuration keys
        val WEBSERVER = DataKey("DOCOPS_WEBSERVER", "http://localhost:8080")
        val DEFAULT_SCALE = DataKey("DOCOPS_DEFAULT_SCALE", "1.0")
        val DEFAULT_TYPE = DataKey("DOCOPS_DEFAULT_TYPE", "svg")
        val DEFAULT_USE_DARK = DataKey("DOCOPS_DEFAULT_USE_DARK", false)
        val DEFAULT_USE_GLASS = DataKey("DOCOPS_DEFAULT_USE_GLASS", "false")
        val DEFAULT_BACKEND = DataKey("DOCOPS_DEFAULT_BACKEND", "html5")

        fun create() = DocOpsMacroExtension()

        /**
         * Compress and Base64 encode the payload
         */
        fun compressAndEncode(content: String): String {
            val byteArrayOutputStream = ByteArrayOutputStream()
            GZIPOutputStream(byteArrayOutputStream).use { gzip ->
                gzip.write(content.toByteArray(Charsets.UTF_8))
            }
            return Base64.getUrlEncoder().encodeToString(byteArrayOutputStream.toByteArray())
        }

        // Patterns for parsing
        private val OPEN_PATTERN = Pattern.compile("""^\[docops:(\w+)(.*?)]\s*$""")
        private val CLOSE_PATTERN = Pattern.compile("""^\[/docops]\s*$""")

        private fun parseOptions(optionsStr: String): Map<String, String> {
            if (optionsStr.isBlank()) return emptyMap()
            val options = mutableMapOf<String, String>()
            // Updated pattern to handle quoted values with spaces
            val optionPattern = Pattern.compile("""(\w+)=(?:"([^"]*)"|'([^']*)'|(\S+))""")
            val matcher = optionPattern.matcher(optionsStr)

            while (matcher.find()) {
                val key = matcher.group(1)
                // Value is in group 2 (double quotes), group 3 (single quotes), or group 4 (unquoted)
                val value = matcher.group(2) ?: matcher.group(3) ?: matcher.group(4)
                options[key] = value
            }
            return options
        }
    }

    override fun rendererOptions(options: MutableDataHolder) {}
    override fun parserOptions(options: MutableDataHolder) {}

    override fun extend(parserBuilder: Parser.Builder) {
        parserBuilder.customBlockParserFactory(DocOpsBlockParserFactory())
    }

    override fun extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String) {
        htmlRendererBuilder.nodeRendererFactory(DocOpsMacroRenderer.Factory())
    }

    /**
     * Custom block parser factory for DocOps macros
     */
    class DocOpsBlockParserFactory : CustomBlockParserFactory {
        override fun getAfterDependents(): Set<Class<*>>? = null
        override fun getBeforeDependents(): Set<Class<*>>? = null
        override fun affectsGlobalScope(): Boolean = false

        override fun apply(options: DataHolder): BlockParserFactory {
            return DocOpsBlockParserFactoryImpl(options)
        }
    }

    /**
     * Block parser factory implementation with tryStart
     */
    class DocOpsBlockParserFactoryImpl(private val options: DataHolder) : AbstractBlockParserFactory(options) {
        override fun tryStart(
            state: ParserState,
            matchedBlockParser: MatchedBlockParser
        ): BlockStart? {
            val line = state.line
            val matcher = OPEN_PATTERN.matcher(line)

            if (matcher.find()) {
                val kind = matcher.group(1)
                val optionsStr = matcher.group(2)?.trim() ?: ""
                val parsedOptions = parseOptions(optionsStr)

                return BlockStart.of(DocOpsBlockParser(kind, parsedOptions))
                    .atIndex(line.length)
            }
            return BlockStart.none()
        }
    }

    /**
     * Block parser that captures everything between [docops:kind] and [/docops]
     */
    class DocOpsBlockParser(
        private val kind: String,
        private val options: Map<String, String>
    ) : AbstractBlockParser() {

        private val block = DocOpsMacroBlock()
        private val content = StringBuilder()

        init {
            block.kind = kind
            block.options = options
        }

        override fun getBlock(): Block = block

        override fun tryContinue(state: ParserState): BlockContinue? {
            val line = state.line
            val closeMatcher = CLOSE_PATTERN.matcher(line)

            return if (closeMatcher.find()) {
                // Found closing tag, finalize the block
                block.body = content.toString().trim()
                BlockContinue.finished()
            } else {
                // Continue capturing content
                if (content.isNotEmpty()) content.append("\n")
                content.append(line)
                BlockContinue.atIndex(line.length)
            }
        }

        override fun addLine(state: ParserState, line: BasedSequence) {
            // Lines are handled in tryContinue
        }

        override fun closeBlock(state: ParserState) {
            block.body = content.toString().trim()
        }
    }

    /**
     * Renderer for DocOpsMacroBlock
     */
    class DocOpsMacroRenderer(private val options: DataHolder) : NodeRenderer {
        private val logger = LoggerFactory.getLogger(DocOpsMacroRenderer::class.java)
        private val webserver = WEBSERVER[options]
        private val defaultScale = DEFAULT_SCALE[options]
        private val defaultType = DEFAULT_TYPE[options]
        private val defaultUseDark = DEFAULT_USE_DARK[options]
        private val defaultUseGlass = DEFAULT_USE_GLASS[options]
        private val defaultBackend = DEFAULT_BACKEND[options]

        override fun getNodeRenderingHandlers(): Set<NodeRenderingHandler<*>> {
            return setOf(
                NodeRenderingHandler(DocOpsMacroBlock::class.java) { node, context, html ->
                    render(node, context, html)
                }
            )
        }

        private fun render(node: DocOpsMacroBlock, context: NodeRendererContext, html: HtmlWriter) {
            val kind = node.kind
            val payload = compressAndEncode(node.body)
            val opts = node.options

            // Build URL with parameters
            val urlString = buildString {
                append("$webserver/api/docops/svg?")
                append("kind=${URLEncoder.encode(kind, "UTF-8")}")
                append("&payload=${URLEncoder.encode(payload, "UTF-8")}")
                append("&scale=${opts["scale"] ?: defaultScale}")
                append("&type=${opts["type"] ?: defaultType}")
                append("&useDark=${opts["useDark"] ?: defaultUseDark}")
                opts["title"]?.let { append("&title=${URLEncoder.encode(it, "UTF-8")}") }
                append("&useGlass=${opts["useGlass"] ?: defaultUseGlass}")
                append("&backend=${opts["backend"] ?: defaultBackend}")
                opts["docname"]?.let { append("&docname=${URLEncoder.encode(it, "UTF-8")}") }
                append("&filename=generated.svg")
            }
            val excludeControlsFilter = listOf("badge", "wordcloud", "buttons")
            val controls = if(excludeControlsFilter.contains(kind)) {""} else {"""
                <div class="docops-control-bar">
                 <button class="docops-btn" onclick="openModal(this.closest('.docops-media-card').querySelector('.svg-container'))">VIEW</button>
                 <button class="docops-btn" onclick="docopsData.toggle(this)">DATA</button>
                 <button class="docops-btn" onclick="docopsCopy.url(this)">LINK</button>
                 <button class="docops-btn" onclick="docopsCopy.svg(this)">SVG</button>
                 <button class="docops-btn" onclick="docopsCopy.png(this)">PNG</button>
               </div>
            """.trimIndent()}

            // Render as an object tag for interactive inline SVG
            val svgRaw = getContentFromServer(urlString, debug = true)
            val content = """
                        <div class="docops-media-card" data-url="$urlString">
                           <div class="svg-container">
                             $svgRaw
                           </div>
                           $controls
                           <div class="docops-data-panel" style="display: none;">
                             <div class="docops-data-header">
                                <span>Embedded Data</span>
                                <button onclick="this.closest('.docops-data-panel').style.display='none'">×</button>
                             </div>
                             <div class="docops-data-table-container"></div>
                           </div>
                        </div>
                    """.trimIndent()
            html.raw(content)
        }

        class Factory : NodeRendererFactory {
            override fun apply(options: DataHolder): NodeRenderer {
                return DocOpsMacroRenderer(options)
            }
        }
        fun getContentFromServer(url: String,  debug: Boolean = false): String {
            if (debug) {
                logger.info("getting image from url $url")
            }
            val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build()
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(1))
                .build()
            return try {
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                response.body()
            } catch (e: Exception) {
                logger.error("Error fetching content from $url: ${e.message}", e)
                ""
            }
        }

    }
}


