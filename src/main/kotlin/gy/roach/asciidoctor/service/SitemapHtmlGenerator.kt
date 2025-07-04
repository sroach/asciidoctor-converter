package gy.roach.asciidoctor.service

import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.math.*

class SitemapHtmlGenerator(
    private val baseUrl: String = ""
) {

    data class SitemapNode(
        val path: Path,
        val name: String,
        val isDirectory: Boolean,
        val relativePath: String,
        val url: String,
        val children: MutableList<SitemapNode> = mutableListOf(),
        val nodeId: String = "",
        var isExpanded: Boolean = true,
        var x: Double = 0.0,
        var y: Double = 0.0,
        var level: Int = 0,
        var angle: Double = 0.0,  // For radial layout
        var radius: Double = 0.0  // Distance from center
    )

    fun generateSitemap(rootPath: Path): String {
        val rootNode = buildTree(rootPath, rootPath)
        return generateRadialInteractiveHtml(rootNode)
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
            nodeId = nodeId,
            isExpanded = true,
            x = 0.0,
            y = 0.0,
            level = 0,
            angle = 0.0,
            radius = 0.0
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

    private fun generateRadialInteractiveHtml(root: SitemapNode): String {
        val allNodes = getAllNodes(root)
        val totalFiles = allNodes.count { !it.isDirectory }
        val totalDirs = allNodes.count { it.isDirectory }
        val sitemapData = generateJsonData(root)

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Radial Sitemap Constellation</title>
    <style>
        ${generateRadialCSS()}
    </style>
</head>
<body>
    <div class="sitemap-container">
        <!-- Header -->
        <div class="sitemap-header">
            <h1 class="sitemap-title">🌌 Radial Sitemap Constellation</h1>
            <div class="sitemap-stats">
                <span class="stat-item">
                    <span class="stat-icon">🌟</span>
                    <span class="stat-label">Root Hub:</span>
                    <span class="stat-value">1</span>
                </span>
                <span class="stat-item">
                    <span class="stat-icon">🌍</span>
                    <span class="stat-label">Orbital Directories:</span>
                    <span class="stat-value">${totalDirs - 1}</span>
                </span>
                <span class="stat-item">
                    <span class="stat-icon">⭐</span>
                    <span class="stat-label">File Stars:</span>
                    <span class="stat-value">$totalFiles</span>
                </span>
                <span class="stat-item">
                    <span class="stat-icon">🌌</span>
                    <span class="stat-label">Total Objects:</span>
                    <span class="stat-value">${allNodes.size}</span>
                </span>
            </div>
            <div class="sitemap-controls">
                <button class="control-btn" onclick="window.sitemapViewer && window.sitemapViewer.expandAll()">
                    <span class="btn-icon">🌟</span>
                    Expand Galaxy
                </button>
                <button class="control-btn" onclick="window.sitemapViewer && window.sitemapViewer.collapseAll()">
                    <span class="btn-icon">🌑</span>
                    Collapse Orbits
                </button>
                <button class="control-btn" onclick="window.sitemapViewer && window.sitemapViewer.resetView()">
                    <span class="btn-icon">🎯</span>
                    Center View
                </button>
                <button class="control-btn" onclick="window.sitemapViewer && window.sitemapViewer.toggleRotation()">
                    <span class="btn-icon">🌪️</span>
                    <span id="rotationLabel">Start Orbit</span>
                </button>
            </div>
        </div>
        
        <!-- Canvas Container -->
        <div class="canvas-container">
            <canvas id="sitemapCanvas" width="1200" height="800"></canvas>
            <div class="canvas-overlay">
                <div id="tooltip" class="tooltip"></div>
            </div>
        </div>
        
        <!-- Footer -->
        <div class="sitemap-footer">
            <p>🌌 <strong>Navigate the Constellation:</strong> Click central hub to expand • Click directories to show/hide orbits • Click files to open • Drag to explore • Scroll to zoom • Enable orbit rotation for animation</p>
        </div>
    </div>

    <script>
        ${generateRadialJavaScript(sitemapData)}
    </script>
</body>
</html>
        """.trimIndent()
    }

    private fun generateRadialCSS(): String {
        return """
/* Radial Sitemap CSS - Space Theme */
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', Arial, sans-serif;
    background: radial-gradient(ellipse at center, #1a1a2e 0%, #16213e 50%, #0f1419 100%);
    color: #ffffff;
    overflow: hidden;
    min-height: 100vh;
}

.sitemap-container {
    display: flex;
    flex-direction: column;
    height: 100vh;
    background: linear-gradient(45deg, #0f1419 0%, #16213e 50%, #1a1a2e 100%);
}

.sitemap-header {
    background: linear-gradient(135deg, #4a90e2 0%, #7b68ee 50%, #ff6b9d 100%);
    color: white;
    padding: 20px;
    box-shadow: 0 4px 20px rgba(0,0,0,0.3);
    position: relative;
    overflow: hidden;
}

.sitemap-header::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: 
        radial-gradient(circle at 20% 80%, rgba(255,255,255,0.1) 1px, transparent 1px),
        radial-gradient(circle at 80% 20%, rgba(255,255,255,0.1) 1px, transparent 1px),
        radial-gradient(circle at 40% 40%, rgba(255,255,255,0.1) 1px, transparent 1px);
    animation: starfield 20s linear infinite;
}

@keyframes starfield {
    0% { transform: translateY(0); }
    100% { transform: translateY(-100px); }
}

.sitemap-title {
    font-size: 32px;
    font-weight: 700;
    margin-bottom: 15px;
    text-align: center;
    text-shadow: 0 0 20px rgba(255,255,255,0.5);
    position: relative;
    z-index: 1;
}

.sitemap-stats {
    display: flex;
    justify-content: center;
    gap: 25px;
    margin-bottom: 20px;
    flex-wrap: wrap;
    position: relative;
    z-index: 1;
}

.stat-item {
    display: flex;
    align-items: center;
    gap: 8px;
    background: rgba(255,255,255,0.15);
    padding: 10px 18px;
    border-radius: 25px;
    backdrop-filter: blur(15px);
    border: 1px solid rgba(255,255,255,0.2);
    box-shadow: 0 4px 15px rgba(0,0,0,0.2);
    transition: all 0.3s ease;
}

.stat-item:hover {
    background: rgba(255,255,255,0.25);
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(0,0,0,0.3);
}

.stat-icon {
    font-size: 18px;
    animation: pulse 2s ease-in-out infinite alternate;
}

@keyframes pulse {
    0% { transform: scale(1); opacity: 0.8; }
    100% { transform: scale(1.1); opacity: 1; }
}

.stat-label {
    font-size: 14px;
    font-weight: 500;
}

.stat-value {
    font-size: 18px;
    font-weight: 700;
    text-shadow: 0 0 10px currentColor;
}

.sitemap-controls {
    display: flex;
    justify-content: center;
    gap: 15px;
    flex-wrap: wrap;
    position: relative;
    z-index: 1;
}

.control-btn {
    background: linear-gradient(135deg, rgba(255,255,255,0.2) 0%, rgba(255,255,255,0.1) 100%);
    color: white;
    border: 1px solid rgba(255,255,255,0.3);
    padding: 12px 22px;
    border-radius: 30px;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 8px;
    transition: all 0.3s ease;
    backdrop-filter: blur(15px);
    box-shadow: 0 4px 15px rgba(0,0,0,0.2);
}

.control-btn:hover {
    background: linear-gradient(135deg, rgba(255,255,255,0.3) 0%, rgba(255,255,255,0.2) 100%);
    transform: translateY(-3px);
    box-shadow: 0 8px 25px rgba(0,0,0,0.3);
    text-shadow: 0 0 10px currentColor;
}

.control-btn:active {
    transform: translateY(-1px);
}

.btn-icon {
    font-size: 16px;
    animation: float 3s ease-in-out infinite;
}

@keyframes float {
    0%, 100% { transform: translateY(0); }
    50% { transform: translateY(-2px); }
}

.canvas-container {
    flex: 1;
    position: relative;
    overflow: hidden;
    background: radial-gradient(ellipse at center, #1a1a2e 0%, #16213e 50%, #0f1419 100%);
}

#sitemapCanvas {
    display: block;
    cursor: grab;
    width: 100%;
    height: 100%;
}

#sitemapCanvas:active {
    cursor: grabbing;
}

.canvas-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    pointer-events: none;
}

.tooltip {
    position: absolute;
    background: linear-gradient(135deg, rgba(0,0,0,0.9) 0%, rgba(30,30,60,0.9) 100%);
    color: white;
    padding: 12px 16px;
    border-radius: 12px;
    font-size: 13px;
    font-weight: 500;
    white-space: nowrap;
    opacity: 0;
    transform: translateY(-100%);
    transition: opacity 0.3s ease;
    pointer-events: none;
    backdrop-filter: blur(15px);
    border: 1px solid rgba(255,255,255,0.2);
    box-shadow: 0 8px 25px rgba(0,0,0,0.4);
    text-shadow: 0 0 10px rgba(255,255,255,0.3);
}

.tooltip.visible {
    opacity: 1;
}

.sitemap-footer {
    background: linear-gradient(135deg, rgba(255,255,255,0.1) 0%, rgba(255,255,255,0.05) 100%);
    padding: 18px 25px;
    text-align: center;
    font-size: 14px;
    color: #b0b0b0;
    border-top: 1px solid rgba(255,255,255,0.1);
    backdrop-filter: blur(15px);
}

.sitemap-footer strong {
    color: #4a90e2;
    text-shadow: 0 0 10px rgba(74,144,226,0.5);
}

/* Responsive design */
@media (max-width: 768px) {
    .sitemap-header {
        padding: 15px;
    }
    
    .sitemap-title {
        font-size: 26px;
    }
    
    .sitemap-stats {
        gap: 15px;
    }
    
    .stat-item {
        padding: 8px 14px;
        font-size: 12px;
    }
    
    .control-btn {
        padding: 10px 18px;
        font-size: 12px;
    }
    
    .sitemap-footer {
        padding: 15px 20px;
        font-size: 12px;
    }
}
        """
    }

    private fun generateRadialJavaScript(sitemapData: String): String {
        return """
// Radial Sitemap Viewer - Space Constellation Theme
class RadialSitemapViewer {
    constructor(canvasId, data) {
        this.canvas = document.getElementById(canvasId);
        this.ctx = this.canvas.getContext('2d');
        this.data = data;
        
        // Validate canvas
        if (!this.canvas || !this.ctx) {
            console.error('Canvas not found or context not available');
            return;
        }
        
        // Canvas properties
        this.scale = 1;
        this.offsetX = 0;
        this.offsetY = 0;
        this.isDragging = false;
        this.lastMouseX = 0;
        this.lastMouseY = 0;
        
        // Radial layout properties
        this.centerX = 0;
        this.centerY = 0;
        this.baseRadius = 120;
        this.radiusIncrement = 100;
        this.minNodeSize = 30;
        this.maxNodeSize = 80;
        this.rootNodeSize = 90;
        
        // Animation properties
        this.isRotating = false;
        this.rotationSpeed = 0.002;
        this.globalRotation = 0;
        this.animationFrame = null;
        this.hoveredNode = null;
        this.tooltip = document.getElementById('tooltip');
        
        // Particle system for connections
        this.particles = [];
        this.maxParticles = 50;
        
        // Colors - space theme
        this.colors = {
            background: '#0f1419',
            centerHub: '#ffd700',
            centerHubGlow: '#ffed4e',
            directory: '#4a90e2',
            directoryGlow: '#6bb6ff',
            file: '#ff6b9d',
            fileGlow: '#ff8fb3',
            connection: '#7b68ee',
            connectionGlow: '#9d7bff',
            particle: '#ffffff'
        };
        
        this.setupCanvas();
        this.setupEventListeners();
        this.calculateRadialLayout();
        this.startAnimationLoop();
    }
    
    setupCanvas() {
        const container = this.canvas.parentElement;
        const dpr = window.devicePixelRatio || 1;
        
        const updateCanvasSize = () => {
            const rect = container.getBoundingClientRect();
            this.canvas.width = rect.width * dpr;
            this.canvas.height = rect.height * dpr;
            this.canvas.style.width = rect.width + 'px';
            this.canvas.style.height = rect.height + 'px';
            this.ctx.scale(dpr, dpr);
            
            // Update center coordinates
            this.centerX = rect.width / 2;
            this.centerY = rect.height / 2;
            
            this.calculateRadialLayout();
        };
        
        updateCanvasSize();
        window.addEventListener('resize', updateCanvasSize);
    }
    
    setupEventListeners() {
        // Mouse events
        this.canvas.addEventListener('mousedown', (e) => this.handleMouseDown(e));
        this.canvas.addEventListener('mousemove', (e) => this.handleMouseMove(e));
        this.canvas.addEventListener('mouseup', (e) => this.handleMouseUp(e));
        this.canvas.addEventListener('wheel', (e) => this.handleWheel(e));
        this.canvas.addEventListener('click', (e) => this.handleClick(e));
        
        // Touch events for mobile
        this.canvas.addEventListener('touchstart', (e) => this.handleTouchStart(e));
        this.canvas.addEventListener('touchmove', (e) => this.handleTouchMove(e));
        this.canvas.addEventListener('touchend', (e) => this.handleTouchEnd(e));
        
        // Prevent context menu
        this.canvas.addEventListener('contextmenu', (e) => e.preventDefault());
    }
    
    calculateRadialLayout() {
        this.assignLevels(this.data, 0);
        
        // Position root at center
        this.data.x = this.centerX;
        this.data.y = this.centerY;
        this.data.radius = 0;
        this.data.angle = 0;
        
        // Layout children in concentric circles
        this.layoutRadialChildren(this.data, 1);
    }
    
    assignLevels(node, level) {
        node.level = level;
        if (node.children) {
            node.children.forEach(child => this.assignLevels(child, level + 1));
        }
    }
    
    layoutRadialChildren(parent, level) {
        if (!parent.children || parent.children.length === 0 || !parent.isExpanded) return;
        
        const visibleChildren = parent.children.filter(child => parent.isExpanded);
        if (visibleChildren.length === 0) return;
        
        const radius = this.baseRadius + (level - 1) * this.radiusIncrement;
        const angleStep = (2 * Math.PI) / visibleChildren.length;
        const startAngle = parent.angle || 0;
        
        visibleChildren.forEach((child, index) => {
            const angle = startAngle + (index * angleStep);
            
            child.radius = radius;
            child.angle = angle;
            child.x = parent.x + radius * Math.cos(angle);
            child.y = parent.y + radius * Math.sin(angle);
            
            // Recursively layout grandchildren
            this.layoutRadialChildren(child, level + 1);
        });
    }
    
    updatePositionsForRotation() {
        if (!this.isRotating) return;
        
        this.globalRotation += this.rotationSpeed;
        this.updateNodePositions(this.data, this.globalRotation);
    }
    
    updateNodePositions(node, rotationOffset) {
        if (node.level > 0 && node.radius > 0) {
            const effectiveAngle = node.angle + rotationOffset;
            const parent = this.findParent(this.data, node);
            if (parent) {
                node.x = parent.x + node.radius * Math.cos(effectiveAngle);
                node.y = parent.y + node.radius * Math.sin(effectiveAngle);
            }
        }
        
        if (node.children && node.isExpanded) {
            node.children.forEach(child => this.updateNodePositions(child, rotationOffset));
        }
    }
    
    findParent(root, targetNode) {
        if (root.children) {
            for (let child of root.children) {
                if (child === targetNode) {
                    return root;
                }
                const found = this.findParent(child, targetNode);
                if (found) return found;
            }
        }
        return null;
    }
    
    startAnimationLoop() {
        const animate = () => {
            this.updatePositionsForRotation();
            this.updateParticles();
            this.render();
            this.animationFrame = requestAnimationFrame(animate);
        };
        animate();
    }
    
    render() {
        const rect = this.canvas.getBoundingClientRect();
        
        // Clear with space background
        this.ctx.fillStyle = this.colors.background;
        this.ctx.fillRect(0, 0, rect.width, rect.height);
        
        // Add starfield effect
        this.drawStarfield();
        
        this.ctx.save();
        this.ctx.translate(this.offsetX, this.offsetY);
        this.ctx.scale(this.scale, this.scale);
        
        // Draw orbital rings
        this.drawOrbitalRings();
        
        // Draw connections with particles
        this.drawConnections(this.data);
        this.drawParticles();
        
        // Draw nodes
        this.drawNodes(this.data);
        
        this.ctx.restore();
    }
    
    drawStarfield() {
        this.ctx.fillStyle = 'rgba(255,255,255,0.8)';
        for (let i = 0; i < 100; i++) {
            const x = Math.random() * this.canvas.width / window.devicePixelRatio;
            const y = Math.random() * this.canvas.height / window.devicePixelRatio;
            const size = Math.random() * 1.5;
            
            this.ctx.beginPath();
            this.ctx.arc(x, y, size, 0, 2 * Math.PI);
            this.ctx.fill();
        }
    }
    
    drawOrbitalRings() {
        const maxLevel = this.getMaxLevel(this.data);
        
        for (let level = 1; level <= maxLevel; level++) {
            const radius = (this.baseRadius + (level - 1) * this.radiusIncrement) * this.scale;
            
            this.ctx.strokeStyle = 'rgba(123, 104, 238, 0.2)';
            this.ctx.lineWidth = 1;
            this.ctx.setLineDash([5, 10]);
            
            this.ctx.beginPath();
            this.ctx.arc(this.centerX, this.centerY, radius, 0, 2 * Math.PI);
            this.ctx.stroke();
        }
        
        this.ctx.setLineDash([]);
    }
    
    getMaxLevel(node) {
        let maxLevel = node.level;
        if (node.children && node.isExpanded) {
            node.children.forEach(child => {
                maxLevel = Math.max(maxLevel, this.getMaxLevel(child));
            });
        }
        return maxLevel;
    }
    
    drawConnections(node) {
        if (!node.children || !node.isExpanded || node.children.length === 0) return;
        
        if (!this.isValidPosition(node.x, node.y)) return;
        
        node.children.forEach(child => {
            if (!this.isValidPosition(child.x, child.y)) return;
            
            // Create gradient line
            const gradient = this.ctx.createLinearGradient(node.x, node.y, child.x, child.y);
            gradient.addColorStop(0, this.colors.connection);
            gradient.addColorStop(1, this.colors.connectionGlow);
            
            this.ctx.strokeStyle = gradient;
            this.ctx.lineWidth = 3;
            this.ctx.globalAlpha = 0.6;
            
            // Draw curved connection
            const midX = (node.x + child.x) / 2;
            const midY = (node.y + child.y) / 2;
            const distance = Math.sqrt((child.x - node.x) ** 2 + (child.y - node.y) ** 2);
            const curveOffset = distance * 0.2;
            
            const perpX = -(child.y - node.y) / distance * curveOffset;
            const perpY = (child.x - node.x) / distance * curveOffset;
            
            this.ctx.beginPath();
            this.ctx.moveTo(node.x, node.y);
            this.ctx.quadraticCurveTo(midX + perpX, midY + perpY, child.x, child.y);
            this.ctx.stroke();
            
            // Add particles along the connection
            if (Math.random() < 0.1 && this.particles.length < this.maxParticles) {
                this.particles.push({
                    x: node.x,
                    y: node.y,
                    targetX: child.x,
                    targetY: child.y,
                    progress: 0,
                    speed: 0.02 + Math.random() * 0.03,
                    size: 2 + Math.random() * 3,
                    life: 1.0
                });
            }
            
            this.drawConnections(child);
        });
        
        this.ctx.globalAlpha = 1;
    }
    
    updateParticles() {
        this.particles = this.particles.filter(particle => {
            particle.progress += particle.speed;
            particle.life -= 0.01;
            
            if (particle.progress >= 1 || particle.life <= 0) {
                return false;
            }
            
            // Update particle position along the curve
            const t = particle.progress;
            particle.x = particle.x * (1 - t) + particle.targetX * t;
            particle.y = particle.y * (1 - t) + particle.targetY * t;
            
            return true;
        });
    }
    
    drawParticles() {
        this.particles.forEach(particle => {
            this.ctx.fillStyle = `rgba(255, 255, 255, ${"$"}{particle.life})`;
            this.ctx.beginPath();
            this.ctx.arc(particle.x, particle.y, particle.size, 0, 2 * Math.PI);
            this.ctx.fill();
        });
    }
    
    isValidPosition(x, y) {
        return typeof x === 'number' && typeof y === 'number' && 
               isFinite(x) && isFinite(y) && 
               !isNaN(x) && !isNaN(y);
    }
    
    drawNodes(node) {
        this.drawNode(node);
        
        if (node.children && node.isExpanded) {
            node.children.forEach(child => this.drawNodes(child));
        }
    }
    
    drawNode(node) {
        if (!this.isValidPosition(node.x, node.y)) return;
        
        const isHovered = this.hoveredNode === node;
        const scale = isHovered ? 1.2 : 1;
        
        // Determine node size and colors
        let nodeSize, fillColor, glowColor;
        
        if (node.level === 0) {
            nodeSize = this.rootNodeSize;
            fillColor = this.colors.centerHub;
            glowColor = this.colors.centerHubGlow;
        } else if (node.isDirectory) {
            nodeSize = this.maxNodeSize - (node.level * 5);
            fillColor = this.colors.directory;
            glowColor = this.colors.directoryGlow;
        } else {
            nodeSize = this.minNodeSize;
            fillColor = this.colors.file;
            glowColor = this.colors.fileGlow;
        }
        
        nodeSize = Math.max(nodeSize, this.minNodeSize) * scale;
        
        this.ctx.save();
        
        // Create radial gradient for glow effect
        const gradient = this.ctx.createRadialGradient(
            node.x, node.y, 0,
            node.x, node.y, nodeSize
        );
        gradient.addColorStop(0, fillColor);
        gradient.addColorStop(0.7, fillColor);
        gradient.addColorStop(1, 'rgba(255,255,255,0.1)');
        
        // Draw glow for root node
        if (node.level === 0 || isHovered) {
            this.ctx.shadowColor = glowColor;
            this.ctx.shadowBlur = 30;
            this.ctx.shadowOffsetX = 0;
            this.ctx.shadowOffsetY = 0;
        }
        
        // Draw main node
        this.ctx.fillStyle = gradient;
        this.ctx.beginPath();
        this.ctx.arc(node.x, node.y, nodeSize / 2, 0, 2 * Math.PI);
        this.ctx.fill();
        
        // Draw border
        this.ctx.strokeStyle = 'rgba(255,255,255,0.8)';
        this.ctx.lineWidth = 2;
        this.ctx.stroke();
        
        // Reset shadow
        this.ctx.shadowColor = 'transparent';
        this.ctx.shadowBlur = 0;
        
        // Draw text
        this.ctx.fillStyle = node.level === 0 ? '#000000' : '#ffffff';
        this.ctx.font = `${"$"}{node.level === 0 ? 'bold' : 'normal'} ${"$"}{Math.max(10, nodeSize / 4)}px -apple-system, BlinkMacSystemFont, SF Pro Display, Arial, sans-serif`;
        this.ctx.textAlign = 'center';
        this.ctx.textBaseline = 'middle';
        
        const displayName = this.truncateText(node.name, node.level === 0 ? 12 : 8);
        this.ctx.fillText(displayName, node.x, node.y);
        
        // Draw expand/collapse indicator for directories with children
        if (node.isDirectory && node.children && node.children.length > 0) {
            this.ctx.fillStyle = node.level === 0 ? '#000000' : '#ffffff';
            this.ctx.font = '16px Arial';
            this.ctx.fillText(
                node.isExpanded ? '−' : '+', 
                node.x, 
                node.y + nodeSize / 2 - 8
            );
        }
        
        this.ctx.restore();
        
        // Store node bounds for hit testing
        node.bounds = {
            x: node.x - nodeSize / 2,
            y: node.y - nodeSize / 2,
            width: nodeSize,
            height: nodeSize
        };
    }
    
    truncateText(text, maxLength) {
        return text.length <= maxLength ? text : text.substring(0, maxLength - 3) + '...';
    }
    
    getMousePos(e) {
        const rect = this.canvas.getBoundingClientRect();
        return {
            x: (e.clientX - rect.left - this.offsetX) / this.scale,
            y: (e.clientY - rect.top - this.offsetY) / this.scale
        };
    }
    
    getNodeAtPosition(x, y) {
        const allNodes = this.getAllNodes(this.data);
        return allNodes.find(node => {
            const bounds = node.bounds;
            return bounds && x >= bounds.x && x <= bounds.x + bounds.width &&
                   y >= bounds.y && y <= bounds.y + bounds.height;
        });
    }
    
    getAllNodes(node, nodes = []) {
        nodes.push(node);
        if (node.children && node.isExpanded) {
            node.children.forEach(child => this.getAllNodes(child, nodes));
        }
        return nodes;
    }
    
    // Event handlers
    handleMouseDown(e) {
        this.isDragging = true;
        this.lastMouseX = e.clientX;
        this.lastMouseY = e.clientY;
    }
    
    handleMouseMove(e) {
        const pos = this.getMousePos(e);
        const hoveredNode = this.getNodeAtPosition(pos.x, pos.y);
        
        if (this.isDragging) {
            const deltaX = e.clientX - this.lastMouseX;
            const deltaY = e.clientY - this.lastMouseY;
            
            this.offsetX += deltaX;
            this.offsetY += deltaY;
            
            this.lastMouseX = e.clientX;
            this.lastMouseY = e.clientY;
        } else {
            // Handle hover
            if (hoveredNode !== this.hoveredNode) {
                this.hoveredNode = hoveredNode;
                
                // Update tooltip
                if (hoveredNode) {
                    this.showTooltip(e, hoveredNode);
                } else {
                    this.hideTooltip();
                }
            }
            
            // Update cursor
            this.canvas.style.cursor = hoveredNode ? 'pointer' : 'grab';
        }
    }
    
    handleMouseUp(e) {
        this.isDragging = false;
    }
    
    handleClick(e) {
        if (this.isDragging) return;
        
        const pos = this.getMousePos(e);
        const clickedNode = this.getNodeAtPosition(pos.x, pos.y);
        
        if (clickedNode) {
            if (clickedNode.isDirectory && clickedNode.children && clickedNode.children.length > 0) {
                // Toggle expand/collapse
                clickedNode.isExpanded = !clickedNode.isExpanded;
                this.calculateRadialLayout();
            } else if (!clickedNode.isDirectory && clickedNode.url) {
                // Open file
                window.open(clickedNode.url, '_blank');
            }
        }
    }
    
    handleWheel(e) {
        e.preventDefault();
        
        const rect = this.canvas.getBoundingClientRect();
        const mouseX = e.clientX - rect.left;
        const mouseY = e.clientY - rect.top;
        
        const wheel = e.deltaY < 0 ? 1 : -1;
        const zoom = Math.exp(wheel * 0.1);
        const newScale = Math.max(0.2, Math.min(3, this.scale * zoom));
        
        if (newScale !== this.scale) {
            const scaleFactor = newScale / this.scale;
            this.offsetX = mouseX - (mouseX - this.offsetX) * scaleFactor;
            this.offsetY = mouseY - (mouseY - this.offsetY) * scaleFactor;
            this.scale = newScale;
        }
    }
    
    // Touch event handlers
    handleTouchStart(e) {
        e.preventDefault();
        if (e.touches.length === 1) {
            const touch = e.touches[0];
            this.handleMouseDown({
                clientX: touch.clientX,
                clientY: touch.clientY
            });
        }
    }
    
    handleTouchMove(e) {
        e.preventDefault();
        if (e.touches.length === 1) {
            const touch = e.touches[0];
            this.handleMouseMove({
                clientX: touch.clientX,
                clientY: touch.clientY
            });
        }
    }
    
    handleTouchEnd(e) {
        e.preventDefault();
        this.handleMouseUp(e);
    }
    
    showTooltip(e, node) {
        if (this.tooltip) {
            let tooltipText = node.relativePath;
            if (node.level === 0) {
                tooltipText += ' (Central Hub)';
            } else if (node.isDirectory) {
                tooltipText += ' (Directory Orbit)';
            } else {
                tooltipText += ' (File Star - Click to open)';
            }
            
            this.tooltip.textContent = tooltipText;
            this.tooltip.style.left = e.clientX + 'px';
            this.tooltip.style.top = (e.clientY - 50) + 'px';
            this.tooltip.classList.add('visible');
        }
    }
    
    hideTooltip() {
        if (this.tooltip) {
            this.tooltip.classList.remove('visible');
        }
    }
    
    // Public methods
    expandAll() {
        this.setExpanded(this.data, true);
        this.calculateRadialLayout();
    }
    
    collapseAll() {
        this.setExpanded(this.data, false);
        this.data.isExpanded = true; // Keep root expanded
        this.calculateRadialLayout();
    }
    
    setExpanded(node, expanded) {
        if (node.isDirectory && node.children) {
            node.isExpanded = expanded;
            node.children.forEach(child => this.setExpanded(child, expanded));
        }
    }
    
    resetView() {
        this.scale = 1;
        this.offsetX = 0;
        this.offsetY = 0;
        this.globalRotation = 0;
        this.calculateRadialLayout();
    }
    
    toggleRotation() {
        this.isRotating = !this.isRotating;
        const label = document.getElementById('rotationLabel');
        if (label) {
            label.textContent = this.isRotating ? 'Stop Orbit' : 'Start Orbit';
        }
    }
}

// Initialize the radial sitemap viewer when DOM is ready
document.addEventListener('DOMContentLoaded', function() {
    try {
        const sitemapData = $sitemapData;
        console.log('Initializing radial sitemap with data:', sitemapData);
        
        if (sitemapData) {
            window.sitemapViewer = new RadialSitemapViewer('sitemapCanvas', sitemapData);
        } else {
            console.error('No sitemap data available');
        }
    } catch (error) {
        console.error('Error initializing radial sitemap viewer:', error);
    }
});
        """
    }

    private fun generateJsonData(root: SitemapNode): String {
        return nodeToJson(root)
    }

    private fun nodeToJson(node: SitemapNode): String {
        val children = if (node.children.isNotEmpty()) {
            node.children.joinToString(",") { nodeToJson(it) }
        } else {
            ""
        }

        return """{
            "name": "${escapeJsonString(node.name)}",
            "relativePath": "${escapeJsonString(node.relativePath)}",
            "url": "${escapeJsonString(node.url)}",
            "isDirectory": ${node.isDirectory},
            "isExpanded": ${node.isExpanded},
            "level": ${node.level},
            "x": ${node.x},
            "y": ${node.y},
            "angle": ${node.angle},
            "radius": ${node.radius},
            "children": [${children}]
        }""".trimIndent()
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

    private fun escapeJsonString(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}