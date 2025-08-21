package gy.roach.asciidoctor.extension

import org.asciidoctor.ast.Document
import org.asciidoctor.extension.DocinfoProcessor
import org.asciidoctor.extension.Location
import org.asciidoctor.extension.LocationType
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringWriter

@Location(LocationType.FOOTER)
class DocOpsMermaidDocinfoProcessor : DocinfoProcessor(){

    override fun process(document: Document): String {
        //language=html
        return """
            <style>
            /* Mermaid fullscreen modal styles */
            .mermaid-modal {
                display: none;
                position: fixed;
                z-index: 10000;
                left: 0;
                top: 0;
                width: 100%;
                height: 100%;
                background-color: rgba(0, 0, 0, 0.9);
                backdrop-filter: blur(5px);
            }
            
            .mermaid-modal.show {
                display: flex;
                align-items: center;
                justify-content: center;
                animation: modalFadeIn 0.3s ease-out;
            }
            
            @keyframes modalFadeIn {
                from { opacity: 0; }
                to { opacity: 1; }
            }
            
            .mermaid-modal-content {
                position: relative;
                max-width: 95vw;
                max-height: 95vh;
                display: flex;
                align-items: center;
                justify-content: center;
                background: white;
                border-radius: 8px;
                padding: 20px;
                box-shadow: 0 10px 25px rgba(0, 0, 0, 0.5);
            }
            
            .mermaid-svg-container {
                background: white;
                border-radius: 4px;
                padding: 10px;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
                max-width: 100%;
                max-height: 100%;
                overflow: hidden;
            }
            
            .mermaid-modal svg {
                max-width: 100%;
                max-height: 100%;
                width: auto;
                height: auto;
                background: white;
                display: block;
            }
            
            .mermaid-modal-close {
                position: absolute;
                top: -10px;
                right: -10px;
                background: rgba(255, 255, 255, 0.95);
                border: none;
                border-radius: 50%;
                width: 40px;
                height: 40px;
                font-size: 20px;
                cursor: pointer;
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 10001;
                transition: all 0.2s ease;
                box-shadow: 0 4px 15px rgba(0, 0, 0, 0.3);
                color: #333;
            }
            
            .mermaid-modal-close:hover {
                background: rgba(255, 255, 255, 1);
                transform: scale(1.1);
                box-shadow: 0 6px 20px rgba(0, 0, 0, 0.4);
            }
            
            .mermaid-modal-controls {
                position: absolute;
                top: -10px;
                left: 10px;
                display: flex;
                gap: 10px;
                z-index: 10001;
            }
            
            .mermaid-modal-btn {
                background: rgba(255, 255, 255, 0.95);
                border: none;
                border-radius: 6px;
                padding: 8px 12px;
                font-size: 12px;
                cursor: pointer;
                transition: all 0.2s ease;
                box-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
                white-space: nowrap;
                color: #333;
            }
            
            .mermaid-modal-btn:hover {
                background: rgba(255, 255, 255, 1);
                transform: translateY(-2px);
                box-shadow: 0 4px 15px rgba(0, 0, 0, 0.4);
            }
            
            /* Mermaid diagram click indicator */
            .mermaid {
                cursor: pointer;
                transition: opacity 0.2s ease;
                position: relative;
            }
            
            .mermaid:hover {
                opacity: 0.8;
            }
            
            .mermaid::after {
                content: '🔍';
                position: absolute;
                top: 10px;
                right: 10px;
                background: rgba(255, 255, 255, 0.9);
                border-radius: 50%;
                width: 30px;
                height: 30px;
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 14px;
                opacity: 0;
                transition: opacity 0.2s ease;
                pointer-events: none;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
            }
            
            .mermaid:hover::after {
                opacity: 1;
            }
            
            /* Responsive adjustments */
            @media (max-width: 768px) {
                .mermaid-modal-content {
                    margin: 10px;
                    max-width: calc(100vw - 20px);
                    max-height: calc(100vh - 20px);
                    padding: 15px;
                }
                
                .mermaid-modal-controls {
                    flex-wrap: wrap;
                    gap: 5px;
                }
                
                .mermaid-modal-btn {
                    font-size: 10px;
                    padding: 6px 8px;
                }
            }
            </style>
            
            <script>
            // Mermaid modal functionality
            window.mermaidModal = {
                currentPanzoom: null,
                
                init() {
                    // Create modal if it doesn't exist
                    if (!document.getElementById('mermaid-modal')) {
                        const modal = document.createElement('div');
                        modal.id = 'mermaid-modal';
                        modal.className = 'mermaid-modal';
                        modal.innerHTML = `
                            <div class="mermaid-modal-content">
                                <button class="mermaid-modal-close" onclick="mermaidModal.close()" title="Close">&times;</button>
                                <div class="mermaid-modal-controls">
                                    <button class="mermaid-modal-btn" onclick="mermaidModal.zoomIn()" title="Zoom In">🔍+</button>
                                    <button class="mermaid-modal-btn" onclick="mermaidModal.zoomOut()" title="Zoom Out">🔍-</button>
                                    <button class="mermaid-modal-btn" onclick="mermaidModal.resetZoom()" title="Reset Zoom">⚪</button>
                                    <button class="mermaid-modal-btn" onclick="mermaidModal.fitToScreen()" title="Fit to Screen">⛶</button>
                                </div>
                                <div class="mermaid-svg-container"></div>
                            </div>
                        `;
                        document.body.appendChild(modal);
                        
                        // Close on background click
                        modal.addEventListener('click', (e) => {
                            if (e.target === modal) {
                                this.close();
                            }
                        });
                        
                        // Close on Escape key
                        document.addEventListener('keydown', (e) => {
                            if (e.key === 'Escape' && modal.classList.contains('show')) {
                                this.close();
                            }
                        });
                    }
                },
                
                open(svg) {
                    this.init();
                    const modal = document.getElementById('mermaid-modal');
                    const container = modal.querySelector('.mermaid-svg-container');
                    
                    // Clone the SVG and ensure it has a white background
                    const clonedSvg = svg.cloneNode(true);
                    
                    // Ensure the SVG has proper styling for visibility
                    clonedSvg.style.backgroundColor = 'white';
                    clonedSvg.style.display = 'block';
                    
                    // Clear and add the cloned SVG
                    container.innerHTML = '';
                    container.appendChild(clonedSvg);
                    
                    // Show modal
                    modal.classList.add('show');
                    document.body.style.overflow = 'hidden';
                    
                    // Initialize panzoom ONLY for the modal SVG
                    setTimeout(() => {
                        if (window.svgPanZoom && clonedSvg) {
                            try {
                                this.currentPanzoom = svgPanZoom(clonedSvg, {
                                    controlIconsEnabled: true,
                                    center: true,
                                    fit: true,
                                    minZoom: 0.1,
                                    maxZoom: 10,
                                    zoomScaleSensitivity: 0.5,
                                    mouseWheelZoomEnabled: true,
                                    preventMouseEventsDefault: true
                                });
                            } catch (error) {
                                console.warn('Failed to initialize panzoom:', error);
                            }
                        }
                    }, 150);
                },
                
                close() {
                    const modal = document.getElementById('mermaid-modal');
                    if (modal) {
                        modal.classList.remove('show');
                        document.body.style.overflow = '';
                        
                        // Cleanup panzoom
                        if (this.currentPanzoom) {
                            try {
                                this.currentPanzoom.destroy();
                            } catch (error) {
                                console.warn('Failed to destroy panzoom:', error);
                            }
                            this.currentPanzoom = null;
                        }
                    }
                },
                
                zoomIn() {
                    if (this.currentPanzoom) {
                        this.currentPanzoom.zoomIn();
                    }
                },
                
                zoomOut() {
                    if (this.currentPanzoom) {
                        this.currentPanzoom.zoomOut();
                    }
                },
                
                resetZoom() {
                    if (this.currentPanzoom) {
                        this.currentPanzoom.resetZoom();
                        this.currentPanzoom.center();
                    }
                },
                
                fitToScreen() {
                    if (this.currentPanzoom) {
                        this.currentPanzoom.fit();
                        this.currentPanzoom.center();
                    }
                }
            };
            
            mermaid.initialize({
                startOnLoad: true,
                theme: 'neo',
                look: 'neo'
            });
            
            mermaid.run({
                querySelector: '.mermaid',
                postRenderCallback: (id) => {
                    console.log('Post-render callback for diagram:', id);
                    const svg = document.getElementById(id);
                    const container = svg.parentNode;
        
                    // NO PANZOOM for embedded diagrams - just basic sizing
                    const bbox = svg.getBBox ? svg.getBBox() : { width: 800, height: 600 };
                    
                    container.style.width = bbox.width + 'px';
                    container.style.height = bbox.height + 'px';
                    svg.setAttribute('width', bbox.width);
                    svg.setAttribute('height', bbox.height);
                    
                    // Add click handler for fullscreen
                    svg.style.cursor = 'pointer';
                    svg.addEventListener('click', () => {
                        mermaidModal.open(svg);
                    });
                    
                    // Add tooltip
                    svg.title = 'Click to open in fullscreen with zoom controls';
                }
            });
            </script>
        """.trimIndent()
    }
}