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
    theme = attrs['theme'] || 'light'
    role = attrs.fetch('role', 'center')

    # Get configuration values directly from document or use defaults
    local_debug = get_debug_setting(parent)
    server = get_server_url(parent)
    webserver = get_webserver_url(parent)

    content = sub_content(reader, parent, local_debug)

    # Fix: Proper create_block call with 4 parameters
    block = create_block(parent, :open, nil, {})
    image = ''

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

      dark = attrs.fetch('useDark', 'false')
      use_dark = dark.downcase == 'true'
      scale = attrs.fetch('scale', '1.0')
      title = attrs.fetch('title', 'Title')
      lines = []
      use_base64 = attrs.fetch('base64', 'true') == 'false'

      if type == 'PDF'
        link = "#{webserver}/api/docops/svg?kind=#{kind}&payload=#{payload}&scale=#{scale}&title=#{CGI.escape(title)}&type=SVG&useDark=#{use_dark}&useGlass=#{use_glass}&backend=#{backend}&docname=#{filename}&filename=docops.svg"
        img = "image::#{link}[#{opts},link=#{link},window=_blank,opts=nofollow]"

        #puts img if local_debug

        lines << img
        parse_content(block, lines)
      else
        url = "#{webserver}/api/docops/svg?kind=#{kind}&payload=#{payload}&scale=#{scale}&type=#{type}&useDark=#{use_dark}&title=#{CGI.escape(title)}&useGlass=#{use_glass}&backend=#{backend}&docname=#{filename}&filename=ghi.svg"

        #puts url if local_debug

        image = get_content_from_server(url, parent)

        # Convert to base64 if requested and it's SVG content
        if use_base64 && !idea_on?(parent)
          image = convert_svg_to_base64_image(image, attrs)
        end

        html = if show_controls && !use_base64
                 generate_svg_viewer_html(image, id, title, show_controls,
                                          allow_copy, allow_zoom, allow_expand, theme, role)
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

  def build_object_tag(data_url, attrs)
    result = "<object type=\"image/svg+xml\" data=\"#{data_url}\""

    # Add standard attributes if provided
    result += " width=\"#{escape_html(attrs['width'])}\"" if attrs['width']
    result += " height=\"#{escape_html(attrs['height'])}\"" if attrs['height']
    result += " class=\"#{escape_html(attrs['class'])}\"" if attrs['class']
    result += " id=\"#{escape_html(attrs['id'])}\"" if attrs['id']
    result += " style=\"#{escape_html(attrs['style'])}\"" if attrs['style']

    # Add ARIA attributes for accessibility
    result += " aria-label=\"#{escape_html(attrs['title'])}\"" if attrs['title']
    result += " aria-describedby=\"#{escape_html(attrs['alt'])}\"" if attrs['alt']

    result += "></object>"
    result
  end

  def svg_content?(content)
    content.strip.start_with?('<svg') && content.include?('</svg>')
  end

  def convert_svg_to_base64_image(svg_content, attrs)
    begin
      # Clean and optimize the SVG content
      clean_svg = optimize_svg_content(svg_content)

      # Encode to base64
      base64_content = Base64.strict_encode64(clean_svg)

      # Create data URL
      data_url = "data:image/svg+xml;base64,#{base64_content}"

      # Build image tag with attributes
      build_object_tag(data_url, attrs)
    rescue => e
      #puts "Warning: Failed to convert SVG to base64: #{e.message}" if @local_debug
      # Return original SVG content if conversion fails
      svg_content
    end
  end

  def optimize_svg_content(svg_content)
    optimized = svg_content.strip

    # Remove XML declaration if present (not needed for data URLs)
    optimized = optimized.gsub(/<\?xml[^>]*\?>/, '')

    # Remove DOCTYPE if present
    optimized = optimized.gsub(/<!DOCTYPE[^>]*>/, '')

    # Remove comments
    optimized = optimized.gsub(/<!--.*?-->/m, '')

    # Normalize whitespace (but be careful with text content)
    optimized = optimized.gsub(/\s+/, ' ')

    # Remove leading/trailing whitespace
    optimized = optimized.strip

    # Ensure xmlns attribute is present for standalone SVG
    unless optimized.include?('xmlns=')
      optimized = optimized.sub('<svg', '<svg xmlns="http://www.w3.org/2000/svg"')
    end

    optimized
  end

  def escape_html(text)
    return '' unless text
    text.to_s.gsub('&', '&amp;')
        .gsub('<', '&lt;')
        .gsub('>', '&gt;')
        .gsub('"', '&quot;')
        .gsub("'", '&#39;')
  end

  def generate_svg_viewer_html(svg_content, id, title, show_controls,
                               allow_copy, allow_zoom, allow_expand, theme, role = 'center')
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

      html << "</div>"
      html << "</div>"
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
end


Asciidoctor::Extensions.register do
  block DocOpsBlockProcessor
end if defined? Asciidoctor::Extensions


