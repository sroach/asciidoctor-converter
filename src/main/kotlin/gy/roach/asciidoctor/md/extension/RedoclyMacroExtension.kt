package gy.roach.asciidoctor.md.extension

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html.HtmlWriter
import com.vladsch.flexmark.html.renderer.NodeRenderer
import com.vladsch.flexmark.html.renderer.NodeRendererContext
import com.vladsch.flexmark.html.renderer.NodeRendererFactory
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler
import com.vladsch.flexmark.parser.InlineParser
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.parser.block.*
import com.vladsch.flexmark.util.ast.Block
import com.vladsch.flexmark.util.data.DataHolder
import com.vladsch.flexmark.util.data.MutableDataHolder
import com.vladsch.flexmark.util.misc.Extension
import com.vladsch.flexmark.util.sequence.BasedSequence
import java.util.regex.Pattern

/**
 * Minimal Redocly markdown macro extension (block form).
 *
 * Supported syntax:
 * [redocly:https://example.com/openapi.yaml title="My API" hideHostname=true primaryColor="#32329f" paths="delete spec.paths['/old'];"]
 *
 * Notes:
 * - Single-line macro, no closing tag required.
 * - Renders raw HTML+JS for HTML renderer.
 */
class RedoclyMacroExtension private constructor() :
    Parser.ParserExtension,
    HtmlRenderer.HtmlRendererExtension {

    companion object {
        private val OPEN_PATTERN = Pattern.compile("""^\[redocly:([^\]\s]+)(.*)]\s*$""")
        private val OPTION_PATTERN = Pattern.compile("""(\w+)=(?:"([^"]*)"|'([^']*)'|(\S+))""")

        fun create(): Extension = RedoclyMacroExtension()

        private fun parseOptions(optionsStr: String): Map<String, String> {
            if (optionsStr.isBlank()) return emptyMap()
            val map = linkedMapOf<String, String>()
            val matcher = OPTION_PATTERN.matcher(optionsStr)
            while (matcher.find()) {
                val key = matcher.group(1)
                val value = matcher.group(2) ?: matcher.group(3) ?: matcher.group(4) ?: ""
                map[key] = value
            }
            return map
        }
    }

    override fun parserOptions(options: MutableDataHolder) = Unit
    override fun rendererOptions(options: MutableDataHolder) = Unit

    override fun extend(parserBuilder: Parser.Builder) {
        parserBuilder.customBlockParserFactory(RedoclyBlockParserFactory())
    }

    override fun extend(rendererBuilder: HtmlRenderer.Builder, rendererType: String) {
        if (rendererType == "HTML") {
            rendererBuilder.nodeRendererFactory(RedoclyNodeRenderer.Factory())
        }
    }

    class RedoclyBlock : Block() {
        var specUrl: String = ""
        var options: Map<String, String> = emptyMap()
        override fun getSegments(): Array<BasedSequence> = EMPTY_SEGMENTS
    }

    class RedoclyBlockParserFactory : CustomBlockParserFactory {
        override fun getAfterDependents(): Set<Class<*>>? = null
        override fun getBeforeDependents(): Set<Class<*>>? = null
        override fun affectsGlobalScope(): Boolean = false

        override fun apply(options: DataHolder): BlockParserFactory = Impl(options)

        private class Impl(options: DataHolder) : AbstractBlockParserFactory(options) {
            override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
                val line = state.line.toString()
                val m = OPEN_PATTERN.matcher(line)
                if (!m.find()) return BlockStart.none()

                val specUrl = (m.group(1) ?: "").trim()
                if (specUrl.isBlank()) return BlockStart.none()

                val optionsStr = (m.group(2) ?: "").trim()
                val options = parseOptions(optionsStr)

                val parser = RedoclyBlockParser(specUrl, options)
                return BlockStart.of(parser).atIndex(state.line.length)
            }
        }
    }

    class RedoclyBlockParser(
        private val specUrl: String,
        private val options: Map<String, String>
    ) : AbstractBlockParser() {

        private val block = RedoclyBlock()

        init {
            block.specUrl = specUrl
            block.options = options
        }

        override fun getBlock(): Block = block

        override fun tryContinue(state: ParserState): BlockContinue = BlockContinue.finished()

        override fun addLine(state: ParserState, line: BasedSequence) = Unit
        override fun isContainer(): Boolean = false
        override fun canContain(state: ParserState, blockParser: BlockParser, block: Block): Boolean = false
        override fun isPropagatingLastBlankLine(lastMatchedBlockParser: BlockParser): Boolean = false
        override fun closeBlock(state: ParserState) = Unit
        override fun parseInlines(inlineParser: InlineParser) = Unit
    }

    class RedoclyNodeRenderer(private val options: DataHolder) : NodeRenderer {

        override fun getNodeRenderingHandlers(): Set<NodeRenderingHandler<*>> {
            return setOf(
                NodeRenderingHandler<RedoclyBlock>(RedoclyBlock::class.java) { node, context, html ->
                    render(node, context, html)
                }
            )
        }

        private fun render(node: RedoclyBlock, context: NodeRendererContext, html: HtmlWriter) {
            val title = node.options["title"] ?: "API Documentation"
            val disableSearch = node.options["disableSearch"]?.toBooleanStrictOrNull() ?: false
            val hideHostname = node.options["hideHostname"]?.toBooleanStrictOrNull() ?: true
            val requiredPropsFirst = node.options["requiredPropsFirst"]?.toBooleanStrictOrNull() ?: true
            val primaryColor = node.options["primaryColor"] ?: "#32329f"
            val token = node.options["token"].orEmpty()
            val paths = node.options["paths"].orEmpty()
            val layout = (node.options["layout"] ?: "fullbleed").trim().lowercase()
            val layoutClass = if (layout == "contained") "redocly-wrap redocly-contained" else "redocly-wrap redocly-fullbleed"

            val containerId = "redoc-container-${System.currentTimeMillis()}-${(Math.random() * 1000).toInt()}"
            val authHeader = if (token.isNotBlank()) "'Authorization': 'Bearer ${jsEsc(token)}'," else ""

            val out = """
            <div class="$layoutClass">
              <div id="$containerId"></div>
            </div>
            <script>
              (function() {
                const specUrl = '${jsEsc(node.specUrl)}';
                const pathsScript = '${jsEsc(paths)}';
                const configOptions = {
                  pageTitle: '${jsEsc(title)}',
                  disableSearch: $disableSearch,
                  hideHostname: $hideHostname,
                  requiredPropsFirst: $requiredPropsFirst,
                  theme: { colors: { primary: { main: '${jsEsc(primaryColor)}' } } }
                };

                fetch(specUrl, {
                  method: 'GET',
                  headers: {
                    'Accept': 'application/json, application/yaml, application/x-yaml, text/yaml, text/x-yaml, text/plain',
                    $authHeader
                  }
                })
                .then(async response => {
                  if (!response.ok) throw new Error(`HTTP error! Status: ${'$'}{response.status}`);
                  const contentType = (response.headers.get('content-type') || '').toLowerCase();
                  const isYaml = /\\.ya?ml($|\\?)/i.test(specUrl)
                    || contentType.includes('yaml')
                    || contentType.includes('x-yaml')
                    || contentType.includes('text/plain');

                  if (isYaml) {
                    if (!window.jsyaml || typeof window.jsyaml.load !== 'function') {
                      throw new Error('YAML detected but js-yaml is not loaded');
                    }
                    return window.jsyaml.load(await response.text());
                  }
                  return response.json();
                })
                .then(spec => {
                  if (!spec.info) spec.info = {};
                  spec.info.title = '${jsEsc(title)}';
                  Redoc.init(spec, configOptions, document.getElementById('$containerId'));
                })
                .catch(err => console.error('Redocly render failed:', err));
              })();
            </script>
        """.trimIndent()

            html.raw(out)
        }

        private fun jsEsc(v: String): String {
            return v
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
        }

        class Factory : NodeRendererFactory {
            override fun apply(options: DataHolder): NodeRenderer = RedoclyNodeRenderer(options)
        }
    }
}