require 'asciidoctor/extensions'
require 'net/http'
require 'uri'
require 'json'
require 'zlib'
require 'base64'
require 'cgi'
require 'securerandom'



include Asciidoctor

class DocOpsBlockProcessor < Extensions::BlockProcessor
  include Asciidoctor::Logging
  use_dsl

  named :docops
  on_contexts :listing
  content_model :compound
  positional_attributes 'kind'
  # Add this helper method at the class level
  def ensure_utf8(str)
    return str if str.nil?
    return str if str.encoding == Encoding::UTF_8 && str.valid_encoding?

    # Handle different encoding scenarios
    if str.encoding == Encoding::ASCII_8BIT
      # Try to detect if it's actually UTF-8
      begin
        utf8_str = str.force_encoding('UTF-8')
        if utf8_str.valid_encoding?
          return utf8_str
        end
      rescue
        # Fall through to replacement strategy
      end
    end

    # Convert with replacement for invalid bytes
    str.to_s.force_encoding('UTF-8').encode('UTF-8', invalid: :replace, undef: :replace)
  end


  def initialize(*args)
    super(*args)
    # Force UTF-8 encoding
    Encoding.default_external = Encoding::UTF_8
    Encoding.default_internal = Encoding::UTF_8

    # Don't set instance variables that will be modified later
  end

  def process(parent, reader, attrs)
    doc = parent.document

    # Get various document identifiers
    docname = doc.attr('docname')           # Filename without extension
    docfile = doc.attr('docfile')           # Full file path
    docdir = doc.attr('docdir')             # Directory containing the file

    # Extract just the filename
    filename = File.basename(docfile || '', '.*') if docfile


    id = generate_id(attrs)
    title = attrs['title'] || 'SVG Viewer'
    show_controls = attrs['controls'] == 'true'
    allow_copy = attrs.fetch('copy', 'true') == 'true'
    use_glass = attrs.fetch('useGlass', 'true') == 'false'
    allow_zoom = attrs.fetch('zoom', 'true') == 'true'
    allow_expand = attrs.fetch('expand', 'true') == 'true'
    allow_csv = attrs.fetch('csv', 'true') == 'true'
    theme = attrs['theme'] || 'light'
    role = attrs.fetch('role', 'center')

    # Get configuration values directly from document or use defaults
    local_debug = get_debug_setting(parent)
    server = get_server_url(parent)
    webserver = get_webserver_url(parent)

    content = sub_content(reader, parent, local_debug)

    # Fix: Proper create_block call with 4 parameters
    block = create_block(parent, :open, nil, {})

    if server_available?(parent, server)
      type = get_type(parent)
      backend = parent.document.attr('backend') || 'html5'
      payload = get_compressed_payload(parent, content)
      opts = "format=svg,opts=inline,align='#{role}'"
      kind = attrs['kind']

      if kind.nil? || kind.empty?
        return create_block(parent, :paragraph,
                            "Parameter Error: Missing 'kind' block parameter, example -> [\"docops\", kind=\"buttons\"] 😵",
                            {})
      end

      # Special handling for mermaid kind
      if kind == "mermaid"
        dark = attrs.fetch('useDark', 'false')
        use_dark = dark.downcase == 'true'
        scale = attrs.fetch('scale', '1.0')
        title = attrs.fetch('title', 'Title')

        url = "#{webserver}/api/docops/svg?kind=#{kind}&payload=#{payload}&scale=#{scale}&type=#{type}&useDark=#{use_dark}&title=#{CGI.escape(title)}&useGlass=#{use_glass}&backend=#{backend}&docname=#{filename}&filename=mermaid.svg"

        mermaid_content = get_content_from_server(url, parent)

        return create_block(parent, :pass, ensure_utf8(mermaid_content), {})
      end

      dark = attrs.fetch('useDark', 'false')
      use_dark = dark.downcase == 'true'
      scale = attrs.fetch('scale', '1.0')
      title = attrs.fetch('title', 'Title')
      lines = []

      if type == 'PDF'
        link = "#{webserver}/api/docops/svg?kind=#{kind}&payload=#{payload}&scale=#{scale}&title=#{CGI.escape(title)}&type=SVG&useDark=#{use_dark}&useGlass=#{use_glass}&backend=#{backend}&docname=#{filename}&filename=docops.svg"
        img = "image::#{link}[#{opts},link=#{link},window=_blank,opts=nofollow]"

        #puts img if local_debug

        lines << img
        parse_content(block, lines)
      else
        url = "#{webserver}/api/docops/svg?kind=#{kind}&payload=#{payload}&scale=#{scale}&type=#{type}&useDark=#{use_dark}&title=#{CGI.escape(title)}&useGlass=#{use_glass}&backend=#{backend}&docname=#{filename}&filename=ghi.svg"

        image = get_content_from_server(url, parent)

        html = if show_controls
                 generate_svg_viewer_html(
                   image, id, title,
                   show_controls, allow_copy, allow_zoom, allow_expand,  theme, role, allow_csv
                 ).gsub(
                   %(<div class="svg-with-controls" id="#{id}" data-theme="#{theme}">),
                   %(<div class="svg-with-controls" id="#{id}" data-theme="#{theme}" data-original-content="#{content.gsub('"', '&quot;')}" data-kind="#{kind}">)
                 )

               else
                 # For non-controlled SVGs, still apply alignment
                 "<div style=\"#{get_alignment_style(role)}\"><div style=\"display: inline-block;\">#{ensure_utf8(image)}</div></div>"
               end
        return create_block(parent, :pass, ensure_utf8(html), {})
      end
    else
      return create_block(parent, :paragraph, "DocOps Server Unavailable! 😵", {})
    end

    block
  end


  private

  # Helper methods that don't modify instance variables
  def get_debug_setting(parent)
    debug = parent.document.attr('local-debug')
    debug&.downcase == 'true'
  end

  def get_server_url(parent)
    parent.document.attr('panel-server') || 'http://localhost:8010'
  end

  def get_webserver_url(parent)
    parent.document.attr('panel-webserver') || 'http://localhost:8010'
  end

  def generate_id(attrs)
    attrs['id'] || "svgviewer-#{SecureRandom.hex(8)}"
  end



  def generate_svg_viewer_html(svg_content, id, title, show_controls,
                               allow_copy, allow_zoom, allow_expand, theme, role = 'center', allow_csv = true)
    # Get alignment styling based on role
    alignment_style = get_alignment_style(role)

    html = []
    html << "<div class=\"svg-viewer-container\" style=\"#{alignment_style}\">"
    html << "<div class=\"svg-with-controls\" id=\"#{id}\" data-theme=\"#{theme}\">"
    html << ensure_utf8(svg_content)

    if show_controls
      html << "<div class=\"svg-floating-controls\">"
      html << "<button class=\"svg-controls-toggle\" onclick=\"svgViewer.toggleControls('#{id}')\" title=\"Controls\">⚙️</button>"
      html << "<div class=\"svg-controls-panel\" id=\"controls-panel-#{id}\">"

      html << ensure_utf8(build_zoom_controls(id)) if allow_zoom
      html << ensure_utf8(build_expand_control(id)) if allow_expand
      html << ensure_utf8(build_copy_control(id)) if allow_copy
      html << ensure_utf8(build_csv_control(id)) if allow_csv

      html << "</div>"
      html << "</div>"
      html << <<~HTML.strip
        <div class="csv-container" id="csv-container-#{id}" style="display: none;">
            <div class="csv-header">
                <span>CSV Data</span>
                <button class="csv-close" onclick="svgViewer.closeCsv('#{id}')" title="Close CSV">×</button>
            </div>
            <div class="csv-content" id="csv-content-#{id}">
                <div class="csv-loading">Loading CSV data...</div>
            </div>
        </div>
      HTML

    end

    html << "</div>" # Close svg-with-controls
    html << "</div>" # Close svg-viewer-container
    html << ensure_utf8(get_minimal_controls_assets)

    # FIX: Ensure all strings are UTF-8 encoded before joining
    html.map! { |item|
      item.to_s.force_encoding('UTF-8').encode('UTF-8', invalid: :replace, undef: :replace)
    }
    html.join("\n")
  end


  def get_alignment_style(role)
    case role.downcase
    when 'left'
      'display: block; text-align: left;'
    when 'right'
      'display: block; text-align: right;'
    when 'center'
      'display: block; text-align: center;'
    else
      'display: block; text-align: center;' # default to center
    end
  end

  def build_zoom_controls(id)
    <<~HTML
      <button class="svg-control-btn zoom-in" onclick="svgViewer.zoomIn('#{id}')" title="Zoom In">🔍+</button>
      <button class="svg-control-btn zoom-out" onclick="svgViewer.zoomOut('#{id}')" title="Zoom Out">🔍-</button>
      <button class="svg-control-btn zoom-reset" onclick="svgViewer.resetZoom('#{id}')" title="Reset Zoom">⚪</button>
    HTML
  end

  def build_expand_control(id)
    <<~HTML
      <button class="svg-control-btn expand" onclick="svgViewer.toggleFullscreen('#{id}')" title="Toggle Fullscreen">⛶</button>
    HTML
  end

  def build_copy_control(id)
    <<~HTML
      <button class="svg-control-btn" onclick="svgViewer.copyAsSvg('#{id}')" title="Copy as SVG">📋 SVG</button>
      <button class="svg-control-btn" onclick="svgViewer.copyAsPng('#{id}')" title="Copy as PNG">📋 PNG</button>
    HTML
  end

  def build_csv_control(id)
    <<~HTML.strip
      <button class="svg-control-btn csv-btn" onclick="svgViewer.toggleCsv('#{id}')" title="Show CSV Data">📊 CSV</button>
    HTML
  end

  def get_minimal_controls_assets
    # Returns the same CSS and JavaScript as in the Kotlin version
    # (truncated for brevity - would include the full CSS and JS from the original)
    <<~ASSETS

    ASSETS
  end

  def sub_content(reader, parent, debug = false)
    content = reader.readlines.join("\n")
    content = ensure_utf8(content)
    subs(content, parent, debug)
  end

  def subs(content, parent, debug = false)
    pattern = /#\[.*?\]/
    content.gsub(pattern) do |match|
      key = match.gsub(/^#\[|\]$/, '').downcase
      sub_value = parent.document.attr(key)

      if debug
        #puts "Text Substitution for #{match} & value to replace #{sub_value}"
      end

      if sub_value
        if debug
          #puts "content after substituting #{match} -> #{sub_value}"
        end
        sub_value
      else
        match
      end
    end
  end

  def debug_on_off(parent)
    debug = parent.document.attr('local-debug')
    @local_debug = debug&.downcase == 'true'
  end

  def setup_servers(parent)
    remote_server = parent.document.attr('panel-server')
    @server = remote_server if remote_server

    remote_webserver = parent.document.attr('panel-webserver')
    @webserver = remote_webserver if remote_webserver
  end

  def get_compressed_payload(parent, content)
    compress_string(content)
  rescue => e
    #puts "Compression error: #{e.message}" if get_debug_setting(parent)
    ''
  end


  def idea_on?(parent)
    env = parent.document.attr('env', '')
    env.downcase == 'idea'
  end

  def get_type(parent)
    backend = parent.document.attr('backend') || 'html5'
    backend.downcase == 'pdf' ? 'PDF' : 'SVG'
  end

  def server_available?(parent, server_url)
    local_debug = get_debug_setting(parent)
    #puts "Checking if server is present #{server_url}/api/ping" if local_debug

    uri = URI("#{server_url}/api/ping")

    begin
      response = Net::HTTP.start(uri.hostname, uri.port,
                                 use_ssl: uri.scheme == 'https',
                                 read_timeout: 60,
                                 open_timeout: 20) do |http|
        http.get(uri.path)
      end

      response.code == '200'
    rescue => e
      #puts "Server availability check failed: #{e.message}" if local_debug
      false
    end
  end

  def get_content_from_server(url, parent)
    local_debug = get_debug_setting(parent)
    #parent.logger.info "Getting content from server: #{url}"
    logger.info "getting image from url #{url}"

    uri = URI(url)

    begin
      response = Net::HTTP.start(uri.hostname, uri.port,
                                 use_ssl: uri.scheme == 'https',
                                 read_timeout: 60,
                                 open_timeout: 20) do |http|
        http.get(uri.request_uri)
      end

      response.body
    rescue => e
      #puts "Failed to get content from server: #{e.message}" if local_debug
      ''
    end
  end


  def compress_string(body)
    compressed = StringIO.new
    gz = Zlib::GzipWriter.new(compressed)
    gz.write(body)
    gz.close
    Base64.urlsafe_encode64(compressed.string)
  end

  def parse_content(block, lines)
    # Implementation for parsing content into the block
    # This would depend on your specific needs
  end

  def safe_log(message)
    begin
      timestamp = Time.now.strftime('%Y-%m-%d %H:%M:%S.%L')
      @log_file.puts "[#{timestamp}] #{message}"
      @log_file.flush
    rescue => e
      # Even file logging can fail, so we silently continue
    end
  end


end

# Register the extension
Extensions.register do
  block DocOpsBlockProcessor
  block DocOpsShowcaseProcessor
end


Asciidoctor::Extensions.register do
  block DocOpsBlockProcessor
  block DocOpsShowcaseProcessor
end if defined? Asciidoctor::Extensions

class DocOpsShowcaseProcessor < Extensions::BlockProcessor
  include Asciidoctor::Logging
  use_dsl

  named :gallery
  on_contexts :example
  content_model :compound
  positional_attributes 'kind'

  def initialize(*args)
    super(*args)
    Encoding.default_external = Encoding::UTF_8
    Encoding.default_internal = Encoding::UTF_8
  end

  def process(parent, reader, attrs)
    kind = attrs['kind']

    # Only process showcase blocks
    return nil unless kind == 'showcase'

    doc = parent.document
    docfile = doc.attr('docfile')
    filename = File.basename(docfile || '', '.*') if docfile

    local_debug = get_debug_setting(parent)
    server = get_server_url(parent)
    webserver = get_webserver_url(parent)
    backend = doc.attr('backend') || 'html5'

    # Check if server is available
    unless server_available?(parent, server)
      return create_block(parent, :paragraph, "DocOps Server Unavailable! 😵", {})
    end

    # Parse nested docops blocks
    image_urls = []
    lines = reader.read_lines

    # Process nested blocks
    nested_blocks = parse_nested_blocks(lines, local_debug)

    nested_blocks.each do |block_data|
      block_kind = block_data[:kind]
      block_content = block_data[:content]
      block_attrs = block_data[:attrs]

      next if block_kind.nil? || block_content.empty?

      # Generate image URL similar to line 129
      payload = compress_string(block_content)
      type = get_type(parent)

      dark = block_attrs.fetch('useDark', 'false')
      use_dark = dark.downcase == 'true'
      scale = block_attrs.fetch('scale', '1.0')
      title = block_attrs.fetch('title', block_kind.capitalize)
      use_glass = block_attrs.fetch('useGlass', 'true') == 'false'

      url = "#{webserver}/api/docops/svg?kind=#{block_kind}&payload=#{payload}&scale=#{scale}&type=#{type}&useDark=#{use_dark}&title=#{CGI.escape(title)}&useGlass=#{use_glass}&backend=#{backend}&docname=#{filename}&filename=#{block_kind}.svg"

      image_urls << {
        url: url,
        title: title,
        kind: block_kind
      }
    end

    # Generate output based on backend
    if backend.downcase == 'html5'
      html_content = generate_html_gallery(image_urls, parent)
      return create_block(parent, :pass, ensure_utf8(html_content), {})
    else
      # Generate AsciiDoc table for PDF and other backends
      table_content = generate_asciidoc_table(image_urls)
      # Parse the table content as AsciiDoc
      block = create_block(parent, :open, nil, {})
      parse_content_lines(block, table_content.split("\n"))
      return block
    end
  end

  private

  def parse_nested_blocks(lines, debug = false)
    blocks = []
    current_block = nil
    in_listing = false

    lines.each do |line|
      # Match [docops,kind] format
      if line.strip =~ /^\[docops,\s*(\w+)(?:,\s*(.+))?\]$/
        # Save previous block if exists
        blocks << current_block if current_block && !current_block[:content].empty?

        kind = $1
        attrs_str = $2

        # Parse additional attributes
        attrs = {}
        if attrs_str
          attrs_str.scan(/(\w+)=["']?([^"',\]]+)["']?/).each do |key, value|
            attrs[key] = value
          end
        end

        current_block = {
          kind: kind,
          content: '',
          attrs: attrs
        }
        in_listing = false
        next
      end

      # Start of listing block
      if line.strip == '....'
        in_listing = !in_listing
        next
      end

      # Collect content if we're in a block and between listing delimiters
      if current_block && in_listing
        current_block[:content] += line + "\n"
      end
    end

    # Add the last block
    blocks << current_block if current_block && !current_block[:content].empty?

    blocks
  end

  def generate_html_gallery(image_urls, parent)
    return "<p>No images to display</p>" if image_urls.empty?

    gallery_id = "docops-gallery-#{SecureRandom.hex(8)}"

    html = []
    html << "<div class=\"docops-showcase-gallery\" id=\"#{gallery_id}\">"

    image_urls.each_with_index do |img_data, index|
      img_url = img_data[:url]
      title = img_data[:title]
      kind = img_data[:kind]
      item_id = "gallery-item-#{gallery_id}-#{index}"

      html << "<div class=\"gallery-item\" data-kind=\"#{kind}\" id=\"#{item_id}\">"
      html << "<div class=\"gallery-item-header\">"
      html << "<div class=\"gallery-item-title\">#{ensure_utf8(title)}</div>"
      html << "<button class=\"gallery-expand-btn\" onclick=\"showcaseGallery.expandItem('#{item_id}', '#{img_url}', '#{ensure_utf8(title).gsub("'", "\\\\'")}')\" title=\"Expand to fullscreen\">⛶</button>"
      html << "</div>"
      html << "<div class=\"gallery-item-image\">"
      html << "<img src=\"#{img_url}\" alt=\"#{ensure_utf8(title)}\" loading=\"lazy\" />"
      html << "</div>"
      html << "</div>"
    end

    html << "</div>"
    html << get_gallery_modal
    html << get_gallery_styles
    html << get_gallery_javascript

    html.join("\n")
  end

  def get_gallery_modal
    <<~HTML
      <div id="showcase-gallery-modal" class="showcase-modal" style="display: none;">
        <div class="showcase-modal-overlay" onclick="showcaseGallery.closeModal()"></div>
        <div class="showcase-modal-content">
          <div class="showcase-modal-header">
            <span class="showcase-modal-title"></span>
            <button class="showcase-modal-close" onclick="showcaseGallery.closeModal()" title="Close">×</button>
          </div>
          <div class="showcase-modal-body">
            <img src="" alt="" class="showcase-modal-image" />
          </div>
        </div>
      </div>
    HTML
  end

  def get_gallery_styles
    <<~CSS
      <style>
        .docops-showcase-gallery {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
          gap: 2rem;
          padding: 2rem;
          margin: 2rem 0;
        }
        
        .gallery-item {
          border: 1px solid #e0e0e0;
          border-radius: 8px;
          padding: 1rem;
          background: #fff;
          box-shadow: 0 2px 4px rgba(0,0,0,0.1);
          transition: transform 0.2s, box-shadow 0.2s;
        }
        
        .gallery-item:hover {
          transform: translateY(-4px);
          box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        }
        
        .gallery-item-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 1rem;
          padding-bottom: 0.5rem;
          border-bottom: 2px solid #3498db;
        }
        
        .gallery-item-title {
          font-weight: bold;
          font-size: 1.1rem;
          color: #2c3e50;
          flex: 1;
        }
        
        .gallery-expand-btn {
          background: none;
          border: none;
          font-size: 1.3rem;
          cursor: pointer;
          padding: 0.25rem 0.5rem;
          color: #3498db;
          transition: color 0.2s, transform 0.2s;
          line-height: 1;
        }
        
        .gallery-expand-btn:hover {
          color: #2980b9;
          transform: scale(1.2);
        }
        
        .gallery-item-image {
          display: flex;
          justify-content: center;
          align-items: center;
          min-height: 200px;
        }
        
        .gallery-item-image img {
          max-width: 100%;
          height: auto;
          cursor: pointer;
        }
        
        .gallery-item-image img:hover {
          opacity: 0.9;
        }
        
        /* Modal Styles */
        .showcase-modal {
          position: fixed;
          top: 0;
          left: 0;
          width: 100%;
          height: 100%;
          z-index: 10000;
          display: flex;
          align-items: center;
          justify-content: center;
        }
        
        .showcase-modal-overlay {
          position: absolute;
          top: 0;
          left: 0;
          width: 100%;
          height: 100%;
          background: rgba(0, 0, 0, 0.8);
          backdrop-filter: blur(4px);
        }
        
        .showcase-modal-content {
          position: relative;
          background: white;
          border-radius: 12px;
          max-width: 95vw;
          max-height: 95vh;
          display: flex;
          flex-direction: column;
          box-shadow: 0 10px 40px rgba(0,0,0,0.3);
          overflow: hidden;
        }
        
        .showcase-modal-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 1rem 1.5rem;
          border-bottom: 1px solid #e0e0e0;
          background: #f8f9fa;
        }
        
        .showcase-modal-title {
          font-weight: bold;
          font-size: 1.2rem;
          color: #2c3e50;
        }
        
        .showcase-modal-close {
          background: none;
          border: none;
          font-size: 2rem;
          cursor: pointer;
          padding: 0;
          width: 2rem;
          height: 2rem;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #7f8c8d;
          transition: color 0.2s;
          line-height: 1;
        }
        
        .showcase-modal-close:hover {
          color: #e74c3c;
        }
        
        .showcase-modal-body {
          padding: 2rem;
          overflow: auto;
          display: flex;
          justify-content: center;
          align-items: center;
          background: white;
        }
        
        .showcase-modal-image {
          max-width: 100%;
          max-height: calc(95vh - 100px);
          width: auto;
          height: auto;
        }
        
        @media (max-width: 768px) {
          .docops-showcase-gallery {
            grid-template-columns: 1fr;
            padding: 1rem;
          }
          
          .showcase-modal-content {
            max-width: 100vw;
            max-height: 100vh;
            border-radius: 0;
          }
          
          .showcase-modal-body {
            padding: 1rem;
          }
        }
      </style>
    CSS
  end

  def get_gallery_javascript
    <<~JAVASCRIPT
      <script>
        window.showcaseGallery = window.showcaseGallery || (function() {
          return {
            expandItem: function(itemId, imageUrl, title) {
              const modal = document.getElementById('showcase-gallery-modal');
              const modalTitle = modal.querySelector('.showcase-modal-title');
              const modalImage = modal.querySelector('.showcase-modal-image');
              
              modalTitle.textContent = title;
              modalImage.src = imageUrl;
              modalImage.alt = title;
              
              modal.style.display = 'flex';
              document.body.style.overflow = 'hidden';
              
              // Close on ESC key
              document.addEventListener('keydown', this.handleEscKey);
            },
            
            closeModal: function() {
              const modal = document.getElementById('showcase-gallery-modal');
              modal.style.display = 'none';
              document.body.style.overflow = '';
              
              document.removeEventListener('keydown', this.handleEscKey);
            },
            
            handleEscKey: function(e) {
              if (e.key === 'Escape') {
                showcaseGallery.closeModal();
              }
            }
          };
        })();
      </script>
    JAVASCRIPT
  end

  def generate_asciidoc_table(image_urls)
    return "" if image_urls.empty?

    # Calculate number of columns (2 for reasonable layout in PDF)
    cols = 2

    lines = []
    lines << "[cols=\"#{cols}*\", frame=none, grid=none]"
    lines << "|==="

    image_urls.each_slice(cols) do |row_images|
      # Title row
      title_row = row_images.map { |img| "a| *#{img[:title]}*" }.join(" ")
      lines << title_row

      # Image row
      image_row = row_images.map do |img|
        "a| image::#{img[:url]}[align=center,opts=inline]"
      end.join(" ")
      lines << image_row
    end

    lines << "|==="

    lines.join("\n")
  end


  def parse_content_lines(block, lines)
    parsed_lines = Asciidoctor::Reader.new(lines)
    while parsed_lines.has_more_lines?
      block << create_block(block, :paragraph, parsed_lines.read_line, {})
    end
  end

  # Helper methods (reused from DocOpsBlockProcessor)
  def get_debug_setting(parent)
    debug = parent.document.attr('local-debug')
    debug&.downcase == 'true'
  end

  def get_server_url(parent)
    parent.document.attr('panel-server')
  end

  def get_webserver_url(parent)
    parent.document.attr('panel-webserver')
  end

  def get_type(parent)
    backend = parent.document.attr('backend') || 'html5'
    backend.downcase == 'pdf' ? 'PDF' : 'SVG'
  end

  def server_available?(parent, server_url)
    local_debug = get_debug_setting(parent)
    uri = URI("#{server_url}/api/ping")

    begin
      response = Net::HTTP.start(uri.hostname, uri.port,
                                 use_ssl: uri.scheme == 'https',
                                 read_timeout: 60,
                                 open_timeout: 20) do |http|
        http.get(uri.path)
      end

      response.code == '200'
    rescue => e
      false
    end
  end

  def compress_string(body)
    compressed = StringIO.new
    gz = Zlib::GzipWriter.new(compressed)
    gz.write(body)
    gz.close
    Base64.urlsafe_encode64(compressed.string)
  end

  def ensure_utf8(str)
    return str if str.nil?
    return str if str.encoding == Encoding::UTF_8 && str.valid_encoding?

    if str.encoding == Encoding::ASCII_8BIT
      begin
        utf8_str = str.force_encoding('UTF-8')
        return utf8_str if utf8_str.valid_encoding?
      rescue
        # Fall through to replacement strategy
      end
    end

    str.to_s.force_encoding('UTF-8').encode('UTF-8', invalid: :replace, undef: :replace)
  end
end


