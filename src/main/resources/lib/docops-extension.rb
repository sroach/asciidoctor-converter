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
  on_contexts :listing, :example
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
    backend = parent.document.attr('backend') || 'html5'

    kind = attrs['kind']

    if kind.nil? || kind.empty?
      return create_block(parent, :paragraph,
                          "Parameter Error: Missing 'kind' block parameter, example -> [\"docops\", kind=\"buttons\"] 😵",
                          {})
    end

    # Check if server is available
    unless server_available?(parent, server)
      return create_block(parent, :paragraph, "DocOps Server Unavailable! 😵", {})
    end

    # Handle showcase gallery
    if kind == 'showcase'
      showcase = DocOpsShowcaseProcessor.new(parent, server, webserver, backend, filename, local_debug)
      html_content = showcase.process_showcase(reader)

      if backend.downcase == 'html5'
        return create_block(parent, :pass, ensure_utf8(html_content), {})
      else
        # For non-HTML backends, parse as AsciiDoc
        block = create_block(parent, :open, nil, {})
        parse_content_lines(block, html_content.split("\n"))
        return block
      end
    end
    # Handle slideshow
    if kind == 'slideshow'
      showcase = DocOpsShowcaseProcessor.new(parent, server, webserver, backend, filename, local_debug)
      interval = attrs['interval'] || '30000'  # Get interval or default to 30000ms
      html_content = showcase.process_slideshow(reader, interval)

      if backend.downcase == 'html5'
        return create_block(parent, :pass, ensure_utf8(html_content), {})
      else
        # For PDF, use table format like showcase
        block = create_block(parent, :open, nil, {})
        parse_content_lines(block, html_content.split("\n"))
        return block
      end
    end
    content = sub_content(reader, parent, local_debug)

    # Fix: Proper create_block call with 4 parameters
    block = create_block(parent, :open, nil, {})

    type = get_type(parent)
    payload = get_compressed_payload(parent, content)
    opts = "format=svg,opts=inline,align='#{role}'"

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
    title = attrs.fetch('title', '')
    lines = []

    if type == 'PDF'
      link = "#{webserver}/api/docops/svg?kind=#{kind}&payload=#{payload}&scale=#{scale}&title=#{CGI.escape(title)}&type=SVG&useDark=#{use_dark}&useGlass=#{use_glass}&backend=#{backend}&docname=#{filename}&filename=docops.svg"
      #img = "image::#{link}[#{opts},link=#{link},window=_blank,opts=nofollow]"

      #puts img if local_debug

      attrs = {
        'target' => link,
        'alt'    => title,
        'format' => 'svg',
        'align'  => role
      }

      # Only add caption if title is not the default
      if title != ''
        # Increment figure counter
        figure_num = doc.increment_and_store_counter('figure-number', parent)
        # Create caption with figure number
        caption = "Figure #{figure_num}. #{title}"
        attrs['title'] = caption
      end

      # Return the image block
      return create_block(parent, :image, nil, attrs)
    else
      url = "#{webserver}/api/docops/svg?kind=#{kind}&payload=#{payload}&scale=#{scale}&type=#{type}&useDark=#{use_dark}&title=#{CGI.escape(title)}&useGlass=#{use_glass}&backend=#{backend}&docname=#{filename}&filename=ghi.svg"

      image = get_content_from_server(url, parent)

      # Only create caption if title is not the default
      caption_html = ""
      if title != ''
        # Increment figure counter
        figure_num = doc.increment_and_store_counter('figure-number', parent)
        # Create caption
        caption_html = "<div class=\"title\">Figure #{figure_num}. #{title}</div>"
      end

      html = if show_controls
               figure_content = generate_svg_viewer_html(
                 image, id, title,
                 show_controls, allow_copy, allow_zoom, allow_expand,  theme, role, allow_csv
               ).gsub(
                 %(<div class="svg-with-controls docops-media-card" id="#{id}" data-theme="#{theme}">),
                 %(<div class="svg-with-controls docops-media-card" id="#{id}" data-theme="#{theme}" data-url="#{url}" data-original-content="#{content.gsub('"', '&quot;')}" data-kind="#{kind}">)
               )

               # Caption AFTER the image content
               "<div class=\"imageblock #{role}\">" + figure_content + caption_html + "</div>"
             else
               # For non-controlled SVGs, caption AFTER the image
               "<div class=\"imageblock #{role}\">" +
                 "<div class=\"content\"><div style=\"#{get_alignment_style(role)}\"><div style=\"display: inline-block;\">#{ensure_utf8(image)}</div></div></div>" +
                 caption_html +
                 "</div>"
             end
      return create_block(parent, :pass, ensure_utf8(html), {})
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

    # Add CSS for bottom controls
    html << <<~CSS
          <style>
            .svg-with-controls {
              position: relative;
              display: inline-block;
              border: 1px solid rgba(255,255,255,0.1);
              border-radius: 8px;
              overflow: hidden;
            }
            .svg-bottom-controls {
              position: absolute;
              bottom: 12px;
              left: 50%;
              transform: translateX(-50%) translateY(150%);
              display: flex;
              gap: 6px;
              background: rgba(15, 20, 30, 0.85);
              padding: 6px 8px;
              border-radius: 6px;
              border: 1px solid rgba(255,255,255,0.1);
              opacity: 0;
              transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
              z-index: 100;
              backdrop-filter: blur(8px);
              box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
            }
            .svg-with-controls:hover .svg-bottom-controls {
              opacity: 1;
              transform: translateX(-50%) translateY(0);
            }
            .svg-control-btn {
              background: transparent;
              border: 1px solid rgba(255,255,255,0.15);
              color: #94a3b8;
              font-family: 'JetBrains Mono', monospace;
              font-size: 10px;
              font-weight: 600;
              padding: 4px 10px;
              border-radius: 4px;
              cursor: pointer;
              transition: all 0.2s;
              text-transform: uppercase;
              letter-spacing: 0.05em;
            }
            .svg-control-btn:hover {
              background: rgba(255,255,255,0.1);
              color: #fff;
              border-color: rgba(255,255,255,0.3);
              transform: translateY(-1px);
            }
          </style>
        CSS

    html << "<div class=\"svg-viewer-container\" style=\"#{alignment_style}\">"
    html << "<div class=\"svg-with-controls docops-media-card\" id=\"#{id}\" data-theme=\"#{theme}\">"
    html << ensure_utf8(svg_content)

    if show_controls
      html << "<div class=\"svg-bottom-controls\">"

      html << "<button class=\"svg-control-btn\" onclick=\"svgViewer.toggleFullscreen('#{id}')\">VIEW</button>" if allow_expand
      html << "<button class=\"svg-control-btn\" onclick=\"svgViewer.toggleCsv('#{id}')\">DATA</button>" if allow_csv
      html << "<button class=\"svg-control-btn\" onclick=\"docopsCopy.url(this)\">LINK</button>" if allow_copy
      html << "<button class=\"svg-control-btn\" onclick=\"svgViewer.copyAsSvg('#{id}')\">SVG</button>" if allow_copy
      html << "<button class=\"svg-control-btn\" onclick=\"svgViewer.copyAsPng('#{id}')\">PNG</button>" if allow_copy

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
      <style>
      imageblock .title {
        text-align: center;
        font-style: italic;
        margin-top: 0.5em;  /* Changed from margin-bottom */
        margin-bottom: 1em;
        color: #666;
        font-size: 0.9em;
      }
      </style>
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

  def parse_content_lines(block, lines)
    parsed_lines = Asciidoctor::Reader.new(lines)
    while parsed_lines.has_more_lines?
      block << create_block(block, :paragraph, parsed_lines.read_line, {})
    end
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
end


Asciidoctor::Extensions.register do
  block DocOpsBlockProcessor
end if defined? Asciidoctor::Extensions

class DocOpsShowcaseProcessor
  attr_reader :parent, :server, :webserver, :backend, :filename, :local_debug

  def initialize(parent, server, webserver, backend, filename, local_debug)
    @parent = parent
    @server = server
    @webserver = webserver
    @backend = backend
    @filename = filename
    @local_debug = local_debug
  end

  def process_showcase(reader)
    lines = reader.read_lines
    nested_blocks = parse_nested_blocks(lines)

    image_urls = []
    nested_blocks.each do |block_data|
      block_kind = block_data[:kind]
      block_content = block_data[:content]
      block_attrs = block_data[:attrs]

      next if block_kind.nil? || block_content.empty?

      # Generate image URL
      payload = compress_string(block_content)
      type = get_type

      dark = block_attrs.fetch('useDark', 'false')
      use_dark = dark.downcase == 'true'
      scale = block_attrs.fetch('scale', '1.0')
      title = block_attrs.fetch('title', block_kind.capitalize)
      use_glass = block_attrs.fetch('useGlass', 'true') == 'false'

      url = "#{webserver}/api/docops/svg?kind=#{block_kind}&payload=#{payload}&scale=#{scale}&type=#{type}&useDark=#{use_dark}&title=#{CGI.escape(title)}&useGlass=#{use_glass}&backend=#{backend}&docname=#{filename}&filename=#{block_kind}.svg"

      image_urls << {
        url: url,
        title: title,
        kind: block_kind,
        content: block_content
      }
    end

    # Generate output based on backend
    if backend.downcase == 'html5'
      generate_html_gallery(image_urls)
    else
      generate_asciidoc_table(image_urls)
    end
  end

  def process_slideshow(reader,  interval = '30000')
    lines = reader.read_lines
    nested_blocks = parse_nested_blocks(lines)

    image_urls = []
    nested_blocks.each do |block_data|
      block_kind = block_data[:kind]
      block_content = block_data[:content]
      block_attrs = block_data[:attrs]

      next if block_kind.nil? || block_content.empty?

      # Generate image URL
      payload = compress_string(block_content)
      type = get_type

      dark = block_attrs.fetch('useDark', 'false')
      use_dark = dark.downcase == 'true'
      scale = block_attrs.fetch('scale', '1.0')
      title = block_attrs.fetch('title', block_kind.capitalize)
      use_glass = block_attrs.fetch('useGlass', 'true') == 'false'

      url = "#{webserver}/api/docops/svg?kind=#{block_kind}&payload=#{payload}&scale=#{scale}&type=#{type}&useDark=#{use_dark}&title=#{CGI.escape(title)}&useGlass=#{use_glass}&backend=#{backend}&docname=#{filename}&filename=#{block_kind}.svg"

      image_urls << {
        url: url,
        title: title,
        kind: block_kind,
        content: block_content
      }
    end

    # Generate output based on backend
    if backend.downcase == 'html5'
      generate_html_slideshow(image_urls, interval)
    else
      generate_asciidoc_table(image_urls)
    end
  end

  private

  def parse_nested_blocks(lines)
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

  def generate_html_gallery(image_urls)
    return "<p>No images to display</p>" if image_urls.empty?

    gallery_id = "docops-gallery-#{SecureRandom.hex(8)}"

    html = []

    # Add the bento grid CSS
    html << <<~CSS
      <style>
        .bento-grid {
          display: grid;
          grid-template-columns: repeat(3, 1fr);
          gap: 16px;
          padding: 48px;
          max-width: 1400px;
          margin: 0 auto;
        }

        .bento-large {
          grid-column: span 2;
          grid-row: span 2;
        }

        .bento-medium {
          grid-column: span 2;
        }

        .bento-grid > div {
          background: linear-gradient(135deg, #1a1f3a 0%, #2d3561 100%);
          border-radius: 24px;
          padding: 32px;
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          position: relative;
          overflow: hidden;
          box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
          transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
        }

        .bento-grid > div::before {
          content: '';
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          bottom: 0;
          background: linear-gradient(135deg, rgba(126, 87, 255, 0.1) 0%, rgba(75, 192, 200, 0.1) 100%);
          opacity: 0;
          transition: opacity 0.4s ease;
          pointer-events: none;
        }

        .bento-grid > div:hover::before {
          opacity: 1;
        }

        .bento-grid > div:hover {
          transform: translateY(-8px);
          box-shadow: 0 16px 48px rgba(126, 87, 255, 0.3);
        }

        .bento-svg-wrapper {
          width: 100%;
          height: 100%;
          display: flex;
          align-items: center;
          justify-content: center;
          position: relative;
          z-index: 1;
        }

        .bento-svg-wrapper svg {
          max-width: 100%;
          max-height: 100%;
          transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
          filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.15));
        }

        .bento-grid > div:hover .bento-svg-wrapper svg {
          transform: scale(1.05) rotate(1deg);
        }

        .bento-label {
          position: absolute;
          top: 16px;
          left: 20px;
          font-family: 'JetBrains Mono', 'Courier New', monospace;
          font-size: 0.65rem;
          font-weight: 700;
          color: rgba(255, 255, 255, 0.5);
          letter-spacing: 0.15em;
          text-transform: uppercase;
          z-index: 2;
          opacity: 0;
          transform: translateY(-10px);
          transition: all 0.3s ease;
        }

        .bento-grid > div:hover .bento-label {
          opacity: 1;
          transform: translateY(0);
        }

        .bento-title {
          position: absolute;
          bottom: 20px;
          left: 20px;
          right: 20px;
          font-family: 'Space Grotesk', -apple-system, sans-serif;
          font-size: 1rem;
          font-weight: 600;
          color: rgba(255, 255, 255, 0.9);
          z-index: 2;
          opacity: 0;
          transform: translateY(10px);
          transition: all 0.3s ease 0.1s;
        }

        .bento-grid > div:hover .bento-title {
          opacity: 1;
          transform: translateY(0);
        }

        .bento-actions {
          position: absolute;
          bottom: 60px;
          left: 20px;
          right: 20px;
          display: flex;
          gap: 8px;
          z-index: 3;
          opacity: 0;
          transform: translateY(10px);
          transition: all 0.3s ease 0.15s;
        }

        .bento-grid > div:hover .bento-actions {
          opacity: 1;
          transform: translateY(0);
        }

        .bento-action-btn {
          flex: 1;
          padding: 8px 16px;
          background: rgba(255, 255, 255, 0.1);
          border: 1px solid rgba(255, 255, 255, 0.2);
          color: rgba(255, 255, 255, 0.9);
          border-radius: 8px;
          font-family: 'JetBrains Mono', monospace;
          font-size: 0.7rem;
          font-weight: 600;
          cursor: pointer;
          transition: all 0.2s ease;
          backdrop-filter: blur(10px);
          letter-spacing: 0.05em;
        }

        .bento-action-btn:hover {
          background: rgba(126, 87, 255, 0.8);
          border-color: rgba(126, 87, 255, 1);
          transform: translateY(-2px);
          box-shadow: 0 4px 12px rgba(126, 87, 255, 0.4);
        }

        /* Modal styles for expanded view */
        .bento-modal {
          position: fixed;
          top: 0;
          left: 0;
          width: 100%;
          height: 100%;
          background: rgba(13, 13, 18, 0.95);
          backdrop-filter: blur(20px);
          display: none;
          align-items: center;
          justify-content: center;
          z-index: 10000;
          opacity: 0;
          transition: opacity 0.3s ease;
        }

        .bento-modal.active {
          display: flex;
          opacity: 1;
        }

        .bento-modal-content {
          background: rgba(26, 31, 58, 0.9);
          border: 1px solid rgba(255, 255, 255, 0.1);
          border-radius: 20px;
          padding: 40px;
          max-width: 90vw;
          max-height: 90vh;
          overflow: auto;
          position: relative;
          transform: scale(0.9);
          transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
        }

        .bento-modal.active .bento-modal-content {
          transform: scale(1);
        }

        .bento-modal-close {
          position: absolute;
          top: 16px;
          right: 16px;
          background: rgba(255, 255, 255, 0.1);
          border: none;
          color: #fff;
          width: 36px;
          height: 36px;
          border-radius: 50%;
          cursor: pointer;
          font-size: 20px;
          display: flex;
          align-items: center;
          justify-content: center;
          transition: all 0.2s ease;
        }

        .bento-modal-close:hover {
          background: rgba(126, 87, 255, 0.8);
          transform: rotate(90deg);
        }

        .bento-modal-body {
          display: flex;
          align-items: center;
          justify-content: center;
          min-height: 400px;
        }

        .bento-modal-body svg {
          max-width: 100%;
          max-height: 80vh;
        }

        .bento-source-modal {
          position: fixed;
          top: 0;
          left: 0;
          width: 100%;
          height: 100%;
          background: rgba(0, 0, 0, 0.9);
          backdrop-filter: blur(10px);
          display: none;
          align-items: center;
          justify-content: center;
          z-index: 10001;
          opacity: 0;
          transition: opacity 0.3s ease;
        }

        .bento-source-modal.active {
          display: flex;
          opacity: 1;
        }

        .bento-source-content {
          background: #1e1e1e;
          border: 1px solid #333;
          border-radius: 12px;
          padding: 24px;
          max-width: 800px;
          width: 90%;
          max-height: 80vh;
          overflow: auto;
          position: relative;
        }

        .bento-source-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 16px;
          padding-bottom: 12px;
          border-bottom: 1px solid #333;
        }

        .bento-source-title {
          font-family: 'Space Grotesk', sans-serif;
          font-size: 1.1rem;
          font-weight: 600;
          color: #fff;
        }

        .bento-source-pre {
          background: #0d0d12;
          border-radius: 8px;
          padding: 16px;
          overflow: auto;
          margin: 0;
        }

        .bento-source-code {
          font-family: 'JetBrains Mono', monospace;
          font-size: 0.85rem;
          line-height: 1.6;
          color: #e0e0e0;
        }

        .bento-source-actions {
          display: flex;
          gap: 8px;
          margin-top: 16px;
        }

        .bento-source-btn {
          padding: 8px 16px;
          background: rgba(126, 87, 255, 0.2);
          border: 1px solid rgba(126, 87, 255, 0.4);
          color: #b392ff;
          border-radius: 6px;
          font-family: 'JetBrains Mono', monospace;
          font-size: 0.75rem;
          cursor: pointer;
          transition: all 0.2s ease;
        }

        .bento-source-btn:hover {
          background: rgba(126, 87, 255, 0.8);
          color: #fff;
        }

        @media (max-width: 1024px) {
          .bento-grid {
            grid-template-columns: repeat(2, 1fr);
            padding: 32px;
          }
          
          .bento-large {
            grid-column: span 2;
            grid-row: span 1;
          }
        }

        @media (max-width: 640px) {
          .bento-grid {
            grid-template-columns: 1fr;
            padding: 16px;
            gap: 12px;
          }
          
          .bento-large,
          .bento-medium {
            grid-column: span 1;
            grid-row: span 1;
          }
        }

        @keyframes bentoFadeIn {
          from {
            opacity: 0;
            transform: translateY(20px) scale(0.95);
          }
          to {
            opacity: 1;
            transform: translateY(0) scale(1);
          }
        }

        .bento-grid > div {
          animation: bentoFadeIn 0.6s cubic-bezier(0.34, 1.56, 0.64, 1) backwards;
        }
      </style>
    CSS

    html << "<div class=\"bento-grid\" id=\"#{gallery_id}\">"

    image_urls.each_with_index do |img_data, index|
      img_url = img_data[:url]
      title = img_data[:title]
      kind = img_data[:kind]
      content = img_data[:content]
      item_id = "gallery-item-#{gallery_id}-#{index}"

      svg_content = get_content_from_server(img_url)
      escaped_content = CGI.escape_html(content)

      # Determine size class based on index pattern
      size_class = case index % 6
                   when 0
                     'bento-large'
                   when 3
                     'bento-medium'
                   else
                     'bento-small'
                   end

      # Animation delay
      delay = (index * 0.1).to_f

      html << "<div class=\"#{size_class}\" id=\"#{item_id}\" style=\"animation-delay: #{delay}s;\">"
      html << "  <span class=\"bento-label\">// #{kind.upcase}</span>"
      html << "  <div class=\"bento-svg-wrapper\">"
      html << "    #{ensure_utf8(svg_content)}"
      html << "  </div>"
      html << "  <div class=\"bento-title\">#{ensure_utf8(title)}</div>"
      html << "  <div class=\"bento-actions\">"
      html << "    <button class=\"bento-action-btn\" onclick=\"showcaseGallery.showContent('#{item_id}', '#{ensure_utf8(title).gsub("'", "\\\\'")}', '#{kind}', this.getAttribute('data-content'))\" data-content=\"#{escaped_content}\">VIEW</button>"
      html << "    <button class=\"bento-action-btn\" onclick=\"showcaseGallery.expandItem('#{item_id}', '#{ensure_utf8(title).gsub("'", "\\\\'")}')\">EXPAND</button>"
      html << "  </div>"
      html << "</div>"
    end

    html << "</div>"

    # Add modals
    html << <<~MODALS
      <div class="bento-modal" id="bento-expand-modal">
        <div class="bento-modal-content">
          <button class="bento-modal-close" onclick="showcaseGallery.closeExpand()">×</button>
          <div class="bento-modal-body" id="bento-expand-body"></div>
        </div>
      </div>

      <div class="bento-source-modal" id="bento-source-modal">
        <div class="bento-source-content">
          <div class="bento-source-header">
            <span class="bento-source-title" id="bento-source-title"></span>
            <button class="bento-modal-close" onclick="showcaseGallery.closeSource()">×</button>
          </div>
          <pre class="bento-source-pre"><code class="bento-source-code" id="bento-source-code"></code></pre>
          <div class="bento-source-actions">
            <button class="bento-source-btn" onclick="showcaseGallery.copySource()">📋 Copy Source</button>
          </div>
        </div>
      </div>
    MODALS

    # Add JavaScript
    html << <<~SCRIPT
      <script>
      const showcaseGallery = {
        expandItem(itemId, title) {
          const item = document.getElementById(itemId);
          const svg = item.querySelector('.bento-svg-wrapper svg');
          const modal = document.getElementById('bento-expand-modal');
          const body = document.getElementById('bento-expand-body');
          
          body.innerHTML = '';
          body.appendChild(svg.cloneNode(true));
          modal.classList.add('active');
          document.body.style.overflow = 'hidden';
        },

        closeExpand() {
          const modal = document.getElementById('bento-expand-modal');
          modal.classList.remove('active');
          document.body.style.overflow = '';
        },

        showContent(itemId, title, kind, content) {
          const modal = document.getElementById('bento-source-modal');
          const titleEl = document.getElementById('bento-source-title');
          const codeEl = document.getElementById('bento-source-code');
          
          titleEl.textContent = title + ' (' + kind + ')';
          codeEl.textContent = decodeURIComponent(content.replace(/\\+/g, ' '));
          modal.classList.add('active');
          document.body.style.overflow = 'hidden';
        },

        closeSource() {
          const modal = document.getElementById('bento-source-modal');
          modal.classList.remove('active');
          document.body.style.overflow = '';
        },

        copySource() {
          const code = document.getElementById('bento-source-code').textContent;
          navigator.clipboard.writeText(code).then(() => {
            const btn = event.target;
            const originalText = btn.textContent;
            btn.textContent = '✓ Copied!';
            setTimeout(() => btn.textContent = originalText, 2000);
          });
        }
      };

      // Close modals on escape key
      document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
          showcaseGallery.closeExpand();
          showcaseGallery.closeSource();
        }
      });

      // Close modals on overlay click
      document.querySelectorAll('.bento-modal, .bento-source-modal').forEach(modal => {
        modal.addEventListener('click', (e) => {
          if (e.target === modal) {
            showcaseGallery.closeExpand();
            showcaseGallery.closeSource();
          }
        });
      });
      </script>
    SCRIPT

    html.join("\n")
  end


  def generate_html_slideshow(image_urls, interval = '30000')
    return "<p>No images to display</p>" if image_urls.empty?

    # Generate a clean ID without hyphens for JavaScript variable names
    slideshow_id = "slideshow#{SecureRandom.hex(8)}"

    html = []

    # Add the card gallery CSS
    html << <<~CSS
    <style>
      :root {
        --slideshow-bg: #0F1419;
        --slideshow-surface: #1A2027;
        --slideshow-accent: #FF6B35;
        --slideshow-accent-dim: #FF8C61;
        --slideshow-text: #E8EAED;
        --slideshow-text-muted: #9BA1A6;
        --slideshow-spacing: 8px;
        --slideshow-radius-card: 16px;
        --slideshow-radius-inner: 8px;
      }

      .slideshow-gallery-container-#{slideshow_id} {
        position: relative;
        max-width: 1400px;
        margin: 3rem auto;
        padding: calc(var(--slideshow-spacing) * 8);
        background: var(--slideshow-bg);
        border-radius: var(--slideshow-radius-card);
        overflow-x: hidden;
      }

      /* Geometric background pattern */
      .slideshow-gallery-container-#{slideshow_id}::before {
        content: '';
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background-image: 
          repeating-linear-gradient(45deg, transparent, transparent 35px, rgba(255, 107, 53, 0.03) 35px, rgba(255, 107, 53, 0.03) 70px),
          repeating-linear-gradient(-45deg, transparent, transparent 35px, rgba(255, 107, 53, 0.02) 35px, rgba(255, 107, 53, 0.02) 70px);
        z-index: 0;
        pointer-events: none;
      }

      .slideshow-gallery-grid-#{slideshow_id} {
        position: relative;
        z-index: 1;
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
        gap: calc(var(--slideshow-spacing) * 3);
        margin-bottom: calc(var(--slideshow-spacing) * 8);
      }

      .svg-slideshow-card-#{slideshow_id} {
        background: var(--slideshow-surface);
        border-radius: var(--slideshow-radius-card);
        padding: calc(var(--slideshow-spacing) * 3);
        cursor: pointer;
        position: relative;
        overflow: hidden;
        transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
        animation: fadeInUpSlide 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
        border: 1px solid rgba(255, 107, 53, 0.1);
      }

      .svg-slideshow-card-#{slideshow_id}:nth-child(1) { animation-delay: 0.1s; }
      .svg-slideshow-card-#{slideshow_id}:nth-child(2) { animation-delay: 0.15s; }
      .svg-slideshow-card-#{slideshow_id}:nth-child(3) { animation-delay: 0.2s; }
      .svg-slideshow-card-#{slideshow_id}:nth-child(4) { animation-delay: 0.25s; }
      .svg-slideshow-card-#{slideshow_id}:nth-child(5) { animation-delay: 0.3s; }
      .svg-slideshow-card-#{slideshow_id}:nth-child(6) { animation-delay: 0.35s; }
      .svg-slideshow-card-#{slideshow_id}:nth-child(n+7) { animation-delay: 0.4s; }

      .svg-slideshow-card-#{slideshow_id}::before {
        content: '';
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 3px;
        background: linear-gradient(90deg, var(--slideshow-accent), var(--slideshow-accent-dim));
        transform: scaleX(0);
        transform-origin: left;
        transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1);
      }

      .svg-slideshow-card-#{slideshow_id}:hover {
        transform: translateY(-4px);
      }

      .svg-slideshow-card-#{slideshow_id}:hover::before {
        transform: scaleX(1);
      }

      .svg-slideshow-preview-#{slideshow_id} {
        width: 100%;
        height: 200px;
        background: var(--slideshow-bg);
        border-radius: var(--slideshow-radius-inner);
        display: flex;
        align-items: center;
        justify-content: center;
        margin-bottom: calc(var(--slideshow-spacing) * 2);
        position: relative;
        overflow: hidden;
      }

      .svg-slideshow-preview-#{slideshow_id} svg {
        max-width: 80%;
        max-height: 80%;
        filter: drop-shadow(0 4px 12px rgba(255, 107, 53, 0.2));
      }

      .svg-slideshow-info-#{slideshow_id} {
        display: flex;
        flex-direction: column;
        gap: calc(var(--slideshow-spacing) * 1);
      }

      .svg-slideshow-name-#{slideshow_id} {
        font-family: 'Courier New', monospace;
        font-size: 16px;
        font-weight: 700;
        color: var(--slideshow-text);
        letter-spacing: 0;
      }

      .svg-slideshow-meta-#{slideshow_id} {
        font-family: 'Courier New', monospace;
        font-size: 12px;
        color: var(--slideshow-text-muted);
      }

      /* Modal for full view */
      .slideshow-modal-#{slideshow_id} {
        display: none;
        position: fixed;
        top: 0;
        left: 0;
        width: 100vw;
        height: 100vh;
        background: rgba(15, 20, 25, 0.95);
        backdrop-filter: blur(8px);
        z-index: 2147483647; /* Max z-index to ensure it's on top */
        align-items: center;
        justify-content: center;
        padding: 40px;
        box-sizing: border-box;
      }

      .slideshow-modal-#{slideshow_id}.active {
        display: flex;
      }

      .slideshow-modal-content-#{slideshow_id} {
        width: 95vw;
        max-width: 1400px;
        height: 90vh;
        background: var(--slideshow-surface);
        border-radius: 20px;
        padding: 48px 24px 24px 24px;
        position: relative;
        animation: scaleInModal 0.4s cubic-bezier(0.16, 1, 0.3, 1);
        border: 2px solid var(--slideshow-accent);
        display: flex;
        flex-direction: column;
        overflow: hidden;
        box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
      }

      .slideshow-modal-close-#{slideshow_id} {
        position: absolute;
        top: 12px;
        right: 12px;
        background: var(--slideshow-accent);
        color: var(--slideshow-bg);
        border: none;
        width: 36px;
        height: 36px;
        border-radius: 50%;
        font-size: 24px;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: transform 0.2s ease;
        font-weight: 700;
        z-index: 10;
      }

      .slideshow-modal-close-#{slideshow_id}:hover {
        transform: rotate(90deg) scale(1.1);
      }

      .slideshow-modal-svg-#{slideshow_id} {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        width: 100%;
        height: 100%;
        overflow: auto; /* Allow scrolling if SVG is truly massive */
        padding: 10px;
      }

      .slideshow-modal-svg-#{slideshow_id} svg {
        max-width: 100%;
        max-height: 100%;
        width: auto;
        height: auto;
        object-fit: contain;
      }

      @keyframes fadeInUpSlide {
        from {
          opacity: 0;
          transform: translateY(24px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }

      @keyframes fadeInModal {
        from { opacity: 0; }
        to { opacity: 1; }
      }

      @keyframes scaleInModal {
        from {
          opacity: 0;
          transform: scale(0.9);
        }
        to {
          opacity: 1;
          transform: scale(1);
        }
      }
    </style>
  CSS

    # JavaScript object definition first
    html << <<~SCRIPT
    <script>
    window.gallery#{slideshow_id} = {
      openModal: function(cardId) {
        const card = document.getElementById(cardId);
        if (!card) {
          console.error('Card not found:', cardId);
          return;
        }
        
        const previewDiv = card.querySelector('.svg-slideshow-preview-#{slideshow_id}');
        if (!previewDiv) {
          console.error('Preview div not found');
          return;
        }
        
        const svg = previewDiv.querySelector('svg');
        if (!svg) {
          console.error('SVG not found in card');
          return;
        }
        
        const modal = document.getElementById('modal-#{slideshow_id}');
        const modalContent = document.getElementById('modalContent-#{slideshow_id}');
        
        if (!modal || !modalContent) {
          console.error('Modal elements not found');
          return;
        }
        
        // CRITICAL FIX: Move modal to body to break out of any CSS transforms/containers
        // that interfere with position: fixed
        if (modal.parentNode !== document.body) {
          document.body.appendChild(modal);
        }
        
        // Clear previous content
        modalContent.innerHTML = '';
        
        // Clone the SVG with deep clone
        const clonedSvg = svg.cloneNode(true);
        
        // Reset explicit width/height attributes if they cause scaling issues
        // We let CSS handle the constraints
        clonedSvg.removeAttribute('width');
        clonedSvg.removeAttribute('height');
        clonedSvg.style.width = '100%';
        clonedSvg.style.height = '100%';
        
        modalContent.appendChild(clonedSvg);
        modal.classList.add('active');
        document.body.style.overflow = 'hidden';
      },

      closeModal: function() {
        const modal = document.getElementById('modal-#{slideshow_id}');
        if (modal) {
          modal.classList.remove('active');
          document.body.style.overflow = '';
        }
      }
    };
    </script>
  SCRIPT

    html << "<div class=\"slideshow-gallery-container-#{slideshow_id}\">"
    html << "<div class=\"slideshow-gallery-grid-#{slideshow_id}\" id=\"galleryGrid-#{slideshow_id}\">"

    # Generate cards for each SVG with inline onclick
    image_urls.each_with_index do |img_data, index|
      img_url = img_data[:url]
      title = img_data[:title]
      kind = img_data[:kind]
      card_id = "card-#{slideshow_id}-#{index}"

      svg_content = get_content_from_server(img_url)

      html << "<div class=\"svg-slideshow-card-#{slideshow_id}\" id=\"#{card_id}\" onclick=\"gallery#{slideshow_id}.openModal('#{card_id}')\">"
      html << "  <div class=\"svg-slideshow-preview-#{slideshow_id}\">"
      html << "    #{ensure_utf8(svg_content)}"
      html << "  </div>"
      html << "  <div class=\"svg-slideshow-info-#{slideshow_id}\">"
      html << "    <div class=\"svg-slideshow-name-#{slideshow_id}\">#{ensure_utf8(title)}</div>"
      html << "    <div class=\"svg-slideshow-meta-#{slideshow_id}\">#{kind.upcase}</div>"
      html << "  </div>"
      html << "</div>"
    end

    html << "</div>" # Close grid
    html << "</div>" # Close container

    # Modal with inline handlers
    html << <<~MODAL
    <div class="slideshow-modal-#{slideshow_id}" id="modal-#{slideshow_id}" onclick="if(event.target.id === 'modal-#{slideshow_id}') gallery#{slideshow_id}.closeModal()">
      <div class="slideshow-modal-content-#{slideshow_id}">
        <button class="slideshow-modal-close-#{slideshow_id}" onclick="event.stopPropagation(); gallery#{slideshow_id}.closeModal()">×</button>
        <div class="slideshow-modal-svg-#{slideshow_id}" id="modalContent-#{slideshow_id}"></div>
      </div>
    </div>
  MODAL

    # Escape key handler
    html << <<~SCRIPT
    <script>
    (function() {
      const handleEscape = function(e) {
        if (e.key === 'Escape') {
          gallery#{slideshow_id}.closeModal();
        }
      };
      document.addEventListener('keydown', handleEscape);
    })();
    </script>
  SCRIPT

    html.join("\n")
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

  def get_content_modal
    <<~HTML
      <div id="showcase-content-modal" class="showcase-modal" style="display: none;">
        <div class="showcase-modal-overlay" onclick="showcaseGallery.closeContentModal()"></div>
        <div class="showcase-modal-content showcase-content-modal-content">
          <div class="showcase-modal-header">
            <span class="showcase-modal-title"></span>
            <button class="showcase-modal-close" onclick="showcaseGallery.closeContentModal()" title="Close">×</button>
          </div>
          <div class="showcase-modal-body showcase-content-body">
            <pre class="showcase-content-pre"><code class="showcase-content-code"></code></pre>
          </div>
          <div class="showcase-content-footer">
            <button class="showcase-copy-btn" onclick="showcaseGallery.copyContent()" title="Copy to clipboard">📋 Copy</button>
          </div>
        </div>
      </div>
    HTML
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
          </div>
        </div>
      </div>
    HTML
  end

  def get_type
    backend.downcase == 'pdf' ? 'PDF' : 'SVG'
  end

  def get_content_from_server(url)
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