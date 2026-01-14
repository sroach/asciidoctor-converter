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
            <div class="modal-overlay" id="modalOverlay" onclick="closeModalOnBackdrop(event)">
                <div class="modal-content" id="modalContent">
                    <button class="close-button" onclick="closeModal()" aria-label="Close modal">×</button>
                    <!-- The JS specifically fails because this ID is missing -->
                    <div id="modalSvgContainer"></div>

                    <div class="modal-zoom-controls">
                        <button class="modal-zoom-btn" onclick="modalZoomOut()" title="Zoom Out">−</button>
                        <span class="modal-zoom-level" id="modalZoomLevel">100%</span>
                        <button class="modal-zoom-btn" onclick="modalZoomIn()" title="Zoom In">+</button>
                        <button class="modal-zoom-btn" onclick="modalZoomReset()" title="Reset Zoom">⟲</button>
                    </div>
                </div>
            </div>
            <script>
              mermaid.initialize({
                startOnLoad: true,
                theme: 'neo',
                look: 'neo'
            });
            </script>
        """.trimIndent()
    }
     fun processOld(document: Document): String {
        //language=html
        return """
            <style>
            /* Universal diagram modal styles */
            .diagram-modal {
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
            
            .diagram-modal.show {
                display: flex;
                align-items: center;
                justify-content: center;
                animation: modalFadeIn 0.3s ease-out;
            }
            
            @keyframes modalFadeIn {
                from { opacity: 0; }
                to { opacity: 1; }
            }
            
            .diagram-modal-content {
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
            
            .diagram-container {
                background: white;
                border-radius: 4px;
                padding: 10px;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
                max-width: 100%;
                max-height: 100%;
                overflow: hidden;
            }
            
            .diagram-modal svg,
            .diagram-modal img {
                max-width: 100%;
                max-height: 100%;
                width: auto;
                height: auto;
                background: white;
                display: block;
            }
            
            .diagram-modal-close {
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
            
            .diagram-modal-close:hover {
                background: rgba(255, 255, 255, 1);
                transform: scale(1.1);
                box-shadow: 0 6px 20px rgba(0, 0, 0, 0.4);
            }
            
            .diagram-modal-controls {
                position: absolute;
                top: -10px;
                left: 10px;
                display: flex;
                gap: 10px;
                z-index: 10001;
            }
            
            .diagram-modal-btn {
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
            
            .diagram-modal-btn:hover {
                background: rgba(255, 255, 255, 1);
                transform: translateY(-2px);
                box-shadow: 0 4px 15px rgba(0, 0, 0, 0.4);
            }
            
            /* Clickable diagram indicators */
            .mermaid,
            .popup-diagram,
            img.popup-diagram {
                cursor: pointer;
                transition: opacity 0.2s ease;
                position: relative;
            }
            
            .mermaid:hover,
            .popup-diagram:hover,
            img.popup-diagram:hover {
                opacity: 0.8;
            }
            
            .mermaid::after,
            .popup-diagram::after,
            img.popup-diagram::after {
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
                z-index: 1;
            }
            
            .mermaid:hover::after,
            .popup-diagram:hover::after,
            img.popup-diagram:hover::after {
                opacity: 1;
            }
            
            /* Responsive adjustments */
            @media (max-width: 768px) {
                .diagram-modal-content {
                    margin: 10px;
                    max-width: calc(100vw - 20px);
                    max-height: calc(100vh - 20px);
                    padding: 15px;
                }
                
                .diagram-modal-controls {
                    flex-wrap: wrap;
                    gap: 5px;
                }
                
                .diagram-modal-btn {
                    font-size: 10px;
                    padding: 6px 8px;
                }
            }
            </style>
            
            <script>
            // Universal diagram modal functionality
            window.diagramModal = {
                currentPanzoom: null,
                currentElement: null,
                
                init() {
                    // Create modal if it doesn't exist
                    if (!document.getElementById('diagram-modal')) {
                        const modal = document.createElement('div');
                        modal.id = 'diagram-modal';
                        modal.className = 'diagram-modal';
                        modal.innerHTML = `
                            <div class="diagram-modal-content">
                                <button class="diagram-modal-close" onclick="diagramModal.close()" title="Close">&times;</button>
                                <div class="diagram-modal-controls">
                                    <button class="diagram-modal-btn" onclick="diagramModal.zoomIn()" title="Zoom In">🔍+</button>
                                    <button class="diagram-modal-btn" onclick="diagramModal.zoomOut()" title="Zoom Out">🔍-</button>
                                    <button class="diagram-modal-btn" onclick="diagramModal.resetZoom()" title="Reset Zoom">⚪</button>
                                    <button class="diagram-modal-btn" onclick="diagramModal.fitToScreen()" title="Fit to Screen">⛶</button>
                                </div>
                                <div class="diagram-container"></div>
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
                
                openSvg(svg) {
                    this.init();
                    const modal = document.getElementById('diagram-modal');
                    const container = modal.querySelector('.diagram-container');
                    
                    // Clone the SVG and ensure it has a white background
                    const clonedSvg = svg.cloneNode(true);
                    
                    // Ensure the SVG has proper styling for visibility
                    clonedSvg.style.backgroundColor = 'white';
                    clonedSvg.style.display = 'block';
                    
                    // Clear and add the cloned SVG
                    container.innerHTML = '';
                    container.appendChild(clonedSvg);
                    
                    this.currentElement = clonedSvg;
                    
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
                                console.warn('Failed to initialize panzoom for SVG:', error);
                            }
                        }
                    }, 150);
                },
                
                openImage(img) {
                    this.init();
                    const modal = document.getElementById('diagram-modal');
                    const container = modal.querySelector('.diagram-container');
                    
                    // Create a new image element
                    const clonedImg = document.createElement('img');
                    clonedImg.src = img.src;
                    clonedImg.alt = img.alt || 'Diagram';
                    clonedImg.style.maxWidth = '100%';
                    clonedImg.style.maxHeight = '100%';
                    clonedImg.style.display = 'block';
                    clonedImg.style.margin = '0 auto';
                    
                    // Clear and add the image
                    container.innerHTML = '';
                    container.appendChild(clonedImg);
                    
                    this.currentElement = clonedImg;
                    
                    // Show modal
                    modal.classList.add('show');
                    document.body.style.overflow = 'hidden';
                    
                    // For images, we'll use CSS transforms for zoom instead of svgPanZoom
                    this.setupImageZoom(clonedImg);
                },
                
                setupImageZoom(img) {
                    let scale = 1;
                    let translateX = 0;
                    let translateY = 0;
                    let isDragging = false;
                    let startX = 0;
                    let startY = 0;
                    let startTranslateX = 0;
                    let startTranslateY = 0;
                    
                    const updateTransform = () => {
                        img.style.transform = `translate(${'$'}{translateX}px, ${'$'}{translateY}px) scale(${'$'}{scale})`;
                    };
                    
                    // Store zoom functions for the modal controls
                    this.currentPanzoom = {
                        zoomIn: () => {
                            scale = Math.min(scale * 1.3, 10);
                            updateTransform();
                        },
                        zoomOut: () => {
                            scale = Math.max(scale / 1.3, 0.1);
                            updateTransform();
                        },
                        resetZoom: () => {
                            scale = 1;
                            translateX = 0;
                            translateY = 0;
                            updateTransform();
                        },
                        fit: () => {
                            scale = 1;
                            translateX = 0;
                            translateY = 0;
                            updateTransform();
                        }
                    };
                    
                    // Mouse wheel zoom
                    img.addEventListener('wheel', (e) => {
                        e.preventDefault();
                        const delta = e.deltaY > 0 ? 0.9 : 1.1;
                        scale = Math.max(0.1, Math.min(10, scale * delta));
                        updateTransform();
                    });
                    
                    // Drag functionality
                    img.addEventListener('mousedown', (e) => {
                        isDragging = true;
                        startX = e.clientX;
                        startY = e.clientY;
                        startTranslateX = translateX;
                        startTranslateY = translateY;
                        img.style.cursor = 'grabbing';
                    });
                    
                    document.addEventListener('mousemove', (e) => {
                        if (!isDragging) return;
                        translateX = startTranslateX + (e.clientX - startX);
                        translateY = startTranslateY + (e.clientY - startY);
                        updateTransform();
                    });
                    
                    document.addEventListener('mouseup', () => {
                        isDragging = false;
                        img.style.cursor = 'grab';
                    });
                    
                    img.style.cursor = 'grab';
                },
                
                close() {
                    const modal = document.getElementById('diagram-modal');
                    if (modal) {
                        modal.classList.remove('show');
                        document.body.style.overflow = '';
                        
                        // Cleanup panzoom
                        if (this.currentPanzoom && this.currentPanzoom.destroy) {
                            try {
                                this.currentPanzoom.destroy();
                            } catch (error) {
                                console.warn('Failed to destroy panzoom:', error);
                            }
                        }
                        this.currentPanzoom = null;
                        this.currentElement = null;
                    }
                },
                
                zoomIn() {
                    if (this.currentPanzoom && this.currentPanzoom.zoomIn) {
                        this.currentPanzoom.zoomIn();
                    }
                },
                
                zoomOut() {
                    if (this.currentPanzoom && this.currentPanzoom.zoomOut) {
                        this.currentPanzoom.zoomOut();
                    }
                },
                
                resetZoom() {
                    if (this.currentPanzoom) {
                        if (this.currentPanzoom.resetZoom) {
                            this.currentPanzoom.resetZoom();
                        }
                        if (this.currentPanzoom.center) {
                            this.currentPanzoom.center();
                        }
                    }
                },
                
                fitToScreen() {
                    if (this.currentPanzoom) {
                        if (this.currentPanzoom.fit) {
                            this.currentPanzoom.fit();
                        }
                        if (this.currentPanzoom.center) {
                            this.currentPanzoom.center();
                        }
                    }
                }
            };
            
            // Initialize popup functionality when DOM is loaded
            document.addEventListener('DOMContentLoaded', function() {
                // Setup both direct img.popup-diagram and div.popup-diagram > img structures
                function setupPopupElement(element) {
                    if (element.hasAttribute('data-popup-initialized')) return;
                    
                    let img = null;
                    let clickTarget = element;
                    
                    if (element.tagName === 'IMG' && element.classList.contains('popup-diagram')) {
                        // Direct img with popup-diagram class
                        img = element;
                    } else if (element.classList.contains('popup-diagram')) {
                        // div with popup-diagram class - find img inside
                        img = element.querySelector('img');
                        if (!img) return; // No img found, skip
                    }
                    
                    if (!img) return;
                    
                    clickTarget.style.cursor = 'pointer';
                    clickTarget.addEventListener('click', (e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        diagramModal.openImage(img);
                    });
                    clickTarget.title = 'Click to open in fullscreen with zoom controls';
                    element.setAttribute('data-popup-initialized', 'true');
                }
                
                // Setup existing popup diagrams
                document.querySelectorAll('img.popup-diagram, .popup-diagram').forEach(setupPopupElement);
            });
            
            // Also check for dynamically added images
            const observer = new MutationObserver(function(mutations) {
                mutations.forEach(function(mutation) {
                    mutation.addedNodes.forEach(function(node) {
                        if (node.nodeType === 1) { // Element node
                            // Check the node itself
                            if ((node.tagName === 'IMG' && node.classList.contains('popup-diagram')) ||
                                (node.classList && node.classList.contains('popup-diagram'))) {
                                setupPopupElement(node);
                            }
                            
                            // Check descendants
                            if (node.querySelectorAll) {
                                const elements = node.querySelectorAll('img.popup-diagram, .popup-diagram');
                                elements.forEach(setupPopupElement);
                            }
                        }
                    });
                });
                
                function setupPopupElement(element) {
                    if (element.hasAttribute('data-popup-initialized')) return;
                    
                    let img = null;
                    let clickTarget = element;
                    
                    if (element.tagName === 'IMG' && element.classList.contains('popup-diagram')) {
                        // Direct img with popup-diagram class
                        img = element;
                    } else if (element.classList.contains('popup-diagram')) {
                        // div with popup-diagram class - find img inside
                        img = element.querySelector('img');
                        if (!img) return; // No img found, skip
                    }
                    
                    if (!img) return;
                    
                    clickTarget.style.cursor = 'pointer';
                    clickTarget.addEventListener('click', (e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        diagramModal.openImage(img);
                    });
                    clickTarget.title = 'Click to open in fullscreen with zoom controls';
                    element.setAttribute('data-popup-initialized', 'true');
                }
            });
            observer.observe(document.body, { childList: true, subtree: true });
            
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
                        diagramModal.openSvg(svg);
                    });
                    
                    // Add tooltip
                    svg.title = 'Click to open in fullscreen with zoom controls';
                }
            });
            </script>
        """.trimIndent()
    }
}