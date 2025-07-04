package gy.roach.asciidoctor.service

import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.relativeTo

class SitemapSvgGenerator(
    private val width: Int = 800,
    private val height: Int = 600,
    private val baseUrl: String = ""
) {

    private val nodeWidth = 140
    private val nodeHeight = 50
    private val verticalSpacing = 80
    private val horizontalSpacing = 160
    private val marginX = 60
    private val marginY = 60
    private val headerHeight = 100  // Space reserved for legend and stats

    data class SitemapNode(
        val path: Path,
        val name: String,
        val isDirectory: Boolean,
        val relativePath: String,
        val url: String,
        val children: MutableList<SitemapNode> = mutableListOf(),
        var x: Int = 0,
        var y: Int = 0,
        var level: Int = 0,
        var nodeId: String = ""
    )

    fun generateSitemap(rootPath: Path): String {
        val rootNode = buildTree(rootPath, rootPath)
        calculatePositions(rootNode)
        return generateIosSvg(rootNode)
    }

    private fun buildTree(path: Path, rootPath: Path): SitemapNode {
        val relativePath = try {
            path.relativeTo(rootPath).toString().replace("\\", "/")
        } catch (e: Exception) {
            path.name
        }

        val url = if (Files.isDirectory(path)) {
            ""
        } else {
            buildUrl(relativePath)
        }

        val nodeId = generateNodeId(path, rootPath)

        val node = SitemapNode(
            path = path,
            name = if (path.name.isBlank()) "root" else path.name,
            isDirectory = Files.isDirectory(path),
            relativePath = relativePath,
            url = url,
            nodeId = nodeId
        )

        if (Files.isDirectory(path)) {
            try {
                Files.list(path).use { stream ->
                    stream.filter { child ->
                        Files.isDirectory(child) || isHtmlFile(child)
                    }.sorted { a, b ->
                        when {
                            Files.isDirectory(a) && !Files.isDirectory(b) -> -1
                            !Files.isDirectory(a) && Files.isDirectory(b) -> 1
                            else -> a.name.compareTo(b.name)
                        }
                    }.forEach { child ->
                        node.children.add(buildTree(child, rootPath))
                    }
                }
            } catch (e: Exception) {
                // Skip directories we can't read
            }
        }

        return node
    }

    private fun generateNodeId(path: Path, rootPath: Path): String {
        val relativePath = try {
            path.relativeTo(rootPath).toString()
        } catch (e: Exception) {
            path.name
        }
        return "node-" + relativePath.replace("[^a-zA-Z0-9]".toRegex(), "-")
    }

    private fun buildUrl(relativePath: String): String {
        return if (baseUrl.isNotEmpty()) {
            "$baseUrl/$relativePath"
        } else {
            relativePath
        }
    }

    private fun isHtmlFile(path: Path): Boolean {
        val extension = path.extension.lowercase()
        return extension in setOf("html", "htm", "xhtml")
    }

    private fun calculatePositions(root: SitemapNode) {
        assignLevels(root, 0)

        // Start nodes below the header area
        root.x = marginX
        root.y = marginY + headerHeight

        layoutChildren(root, marginX + horizontalSpacing, root.y)
    }

    private fun assignLevels(node: SitemapNode, level: Int) {
        node.level = level
        node.children.forEach { child ->
            assignLevels(child, level + 1)
        }
    }

    private fun layoutChildren(parent: SitemapNode, startX: Int, startY: Int) {
        if (parent.children.isEmpty()) return

        val totalHeight = parent.children.size * verticalSpacing
        var currentY = startY - (totalHeight / 2) + (verticalSpacing / 2)

        parent.children.forEach { child ->
            child.x = startX
            child.y = currentY
            currentY += verticalSpacing

            layoutChildren(child, startX + horizontalSpacing, child.y)
        }
    }

    private fun generateIosSvg(root: SitemapNode): String {
        val nodes = getAllNodes(root)

        // Calculate actual bounds of content
        val maxX = nodes.maxOfOrNull { it.x } ?: 0
        val maxY = nodes.maxOfOrNull { it.y } ?: 0
        val minY = nodes.minOfOrNull { it.y } ?: 0

        // Calculate SVG dimensions based on actual content
        val contentWidth = maxX + nodeWidth + marginX
        val contentHeight = maxY - minY + nodeHeight + marginY

        // Ensure minimum height to accommodate header
        val svgWidth = maxOf(contentWidth, 600)  // Minimum width for legend
        val svgHeight = maxOf(contentHeight + headerHeight, 400)  // Minimum total height

        val svg = StringBuilder()
        svg.append("""
            <svg width="$svgWidth" height="$svgHeight" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $svgWidth $svgHeight">
            <defs>
                ${generateIosGradients()}
                <style><![CDATA[
                    ${generateIosStyles()}
                ]]></style>
            </defs>
            
            <!-- iOS Background -->
            <rect width="100%" height="100%" fill="#F2F2F7" />
        """.trimIndent())

        // Add legend and stats in header area
        addIosLegendAndStats(svg, nodes)

        // Draw connections
        drawIosConnections(svg, root)

        // Draw nodes
        drawIosNodes(svg, root)

        svg.append("</svg>")
        return svg.toString()
    }

    private fun generateIosGradients(): String {
        return """
            <!-- iOS Color Gradients -->
            <linearGradient id="ios-blue-gradient" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" style="stop-color:#007AFF;stop-opacity:1" />
                <stop offset="100%" style="stop-color:#0051D0;stop-opacity:1" />
            </linearGradient>
            
            <linearGradient id="ios-green-gradient" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" style="stop-color:#34C759;stop-opacity:1" />
                <stop offset="100%" style="stop-color:#2AA946;stop-opacity:1" />
            </linearGradient>
            
            <linearGradient id="ios-purple-gradient" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" style="stop-color:#AF52DE;stop-opacity:1" />
                <stop offset="100%" style="stop-color:#9B44C8;stop-opacity:1" />
            </linearGradient>
            
            <!-- Connection Line Gradient -->
            <linearGradient id="connection-gradient" x1="0%" y1="0%" x2="100%" y2="0%">
                <stop offset="0%" style="stop-color:#007AFF;stop-opacity:0.8" />
                <stop offset="50%" style="stop-color:#34C759;stop-opacity:0.6" />
                <stop offset="100%" style="stop-color:#AF52DE;stop-opacity:0.8" />
            </linearGradient>
            
            <!-- Shadow Filter -->
            <filter id="ios-shadow" x="-20%" y="-20%" width="140%" height="140%">
                <feDropShadow dx="0" dy="2" stdDeviation="4" flood-opacity="0.15" flood-color="#000000" />
            </filter>
        """
    }

    private fun generateIosStyles(): String {
        return """
            .ios-container {
                filter: url(#ios-shadow);
                transition: all 0.3s ease;
            }
            
            .ios-container:hover {
                transform: scale(1.05);
            }
            
            .root-node {
                stroke: #FFFFFF;
                stroke-width: 2;
            }
            
            .directory-node {
                stroke: #FFFFFF;
                stroke-width: 1.5;
            }
            
            .file-node {
                stroke: #FFFFFF;
                stroke-width: 1.5;
                cursor: pointer;
            }
            
            .file-node:hover {
                stroke: #E5E5EA;
                stroke-width: 2;
            }
            
            .ios-text {
                font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', Arial, sans-serif;
                font-weight: 600;
                text-anchor: middle;
                dominant-baseline: middle;
                fill: #FFFFFF;
                pointer-events: none;
            }
            
            .ios-subtitle {
                font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', Arial, sans-serif;
                font-weight: 400;
                fill: #6D6D80;
            }
            
            .ios-connection {
                stroke: url(#connection-gradient);
                stroke-width: 3;
                fill: none;
                opacity: 0.8;
            }
            
            .legend-container {
                filter: url(#ios-shadow);
            }
        """
    }

    private fun addIosLegendAndStats(svg: StringBuilder, nodes: List<SitemapNode>) {
        val totalFiles = nodes.count { !it.isDirectory }
        val totalDirs = nodes.count { it.isDirectory }

        svg.append("""
            <!-- Fixed Header Area -->
            <g class="header-area">
                <!-- Legend Container -->
                <g class="legend-container" transform="translate(20, 15)">
                    <rect x="0" y="0" width="400" height="35" rx="12" ry="12" 
                          fill="#FFFFFF" stroke="#E5E5EA" stroke-width="1"/>
                    
                    <!-- Directory Legend -->
                    <rect x="10" y="7" width="18" height="18" rx="6" ry="6" 
                          fill="url(#ios-green-gradient)" stroke="#FFFFFF" stroke-width="1"/>
                    <text x="35" y="18" class="ios-subtitle" font-size="12">Directories ($totalDirs)</text>
                    
                    <!-- File Legend -->
                    <rect x="180" y="7" width="18" height="18" rx="6" ry="6" 
                          fill="url(#ios-purple-gradient)" stroke="#FFFFFF" stroke-width="1"/>
                    <text x="205" y="18" class="ios-subtitle" font-size="12">HTML Files ($totalFiles)</text>
                </g>
                
                <!-- Stats Container -->
                <g class="legend-container" transform="translate(20, 60)">
                    <rect x="0" y="0" width="500" height="25" rx="8" ry="8" 
                          fill="#FFFFFF" stroke="#E5E5EA" stroke-width="1"/>
                    <text x="10" y="16" class="ios-subtitle" font-size="11">
                        Total: ${nodes.size} items • Click colored nodes to open HTML files • iOS Design Theme
                    </text>
                </g>
            </g>
        """.trimIndent())
    }

    private fun getAllNodes(root: SitemapNode): List<SitemapNode> {
        val nodes = mutableListOf<SitemapNode>()
        collectNodes(root, nodes)
        return nodes
    }

    private fun collectNodes(node: SitemapNode, nodes: MutableList<SitemapNode>) {
        nodes.add(node)
        node.children.forEach { child ->
            collectNodes(child, nodes)
        }
    }

    private fun drawIosConnections(svg: StringBuilder, node: SitemapNode) {
        node.children.forEach { child ->
            val startX = node.x + nodeWidth
            val startY = node.y + nodeHeight / 2
            val endX = child.x
            val endY = child.y + nodeHeight / 2

            val midX = startX + (endX - startX) / 2

            svg.append("""
                <path d="M $startX $startY L $midX $startY L $midX $endY L $endX $endY" 
                      class="ios-connection"/>
            """.trimIndent())

            drawIosConnections(svg, child)
        }
    }

    private fun drawIosNodes(svg: StringBuilder, node: SitemapNode) {
        val displayName = truncateText(node.name, 18)
        val nodeClass = when {
            node.level == 0 -> "root-node"
            node.isDirectory -> "directory-node"
            else -> "file-node"
        }

        val fillGradient = when {
            node.level == 0 -> "url(#ios-blue-gradient)"
            node.isDirectory -> "url(#ios-green-gradient)"
            else -> "url(#ios-purple-gradient)"
        }

        val clickHandler = if (!node.isDirectory && node.url.isNotEmpty()) {
            """onclick="window.open('${escapeUrl(node.url)}', '_blank')" """
        } else {
            ""
        }

        val nodeSize = when {
            node.level == 0 -> Pair(nodeWidth + 20, nodeHeight + 10)
            node.isDirectory -> Pair(nodeWidth, nodeHeight)
            else -> Pair(nodeWidth - 10, nodeHeight - 5)
        }

        val adjustedX = when {
            node.level == 0 -> node.x - 10
            !node.isDirectory -> node.x + 5
            else -> node.x
        }

        val adjustedY = when {
            node.level == 0 -> node.y - 5
            !node.isDirectory -> node.y + 2
            else -> node.y
        }

        svg.append("""
            <g class="ios-container" ${clickHandler}data-name="${escapeXml(node.name)}" 
               data-path="${escapeXml(node.relativePath)}" data-type="${if (node.isDirectory) "directory" else "file"}">
               
                <!-- Main Container -->
                <rect x="$adjustedX" y="$adjustedY" width="${nodeSize.first}" height="${nodeSize.second}" 
                      rx="12" ry="12" fill="$fillGradient" class="$nodeClass"/>
                
                <!-- Text -->
                <text x="${adjustedX + nodeSize.first / 2}" y="${adjustedY + nodeSize.second / 2}" 
                      class="ios-text" font-size="${if (node.level == 0) 14 else 12}">${escapeXml(displayName)}</text>
                      
                <!-- Tooltip -->
                <title>${escapeXml(node.relativePath)}${if (node.url.isNotEmpty()) " - Click to open" else ""}</title>
            </g>
        """.trimIndent())

        node.children.forEach { child ->
            drawIosNodes(svg, child)
        }
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun escapeUrl(url: String): String {
        return URLEncoder.encode(url, "UTF-8").replace("+", "%20")
    }

    private fun truncateText(text: String, maxLength: Int): String {
        return if (text.length <= maxLength) text else text.take(maxLength - 3) + "..."
    }
}