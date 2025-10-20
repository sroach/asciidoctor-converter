require 'asciidoctor/extensions'
require 'net/http'
require 'uri'
require 'json'
require 'zlib'
require 'base64'
require 'cgi'
require 'securerandom'



include Asciidoctor

# Regular helper class for showcase gallery processing
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
    html << "<div class=\"docops-showcase-gallery\" id=\"#{gallery_id}\">"

    image_urls.each_with_index do |img_data, index|
      img_url = img_data[:url]
      title = img_data[:title]
      kind = img_data[:kind]
      content = img_data[:content]
      item_id = "gallery-item-#{gallery_id}-#{index}"

      # Fetch SVG content inline
      svg_content = get_content_from_server(img_url)

      # Escape content for HTML attribute
      escaped_content = CGI.escape_html(content)

      html << "<div class=\"gallery-item\" data-kind=\"#{kind}\" id=\"#{item_id}\">"
      html << "<div class=\"gallery-item-header\">"
      html << "<div class=\"gallery-item-title\">#{ensure_utf8(title)}</div>"
      html << "<div class=\"gallery-item-actions\">"
      html << "<button class=\"gallery-content-btn\" onclick=\"showcaseGallery.showContent('#{item_id}', '#{ensure_utf8(title).gsub("'", "\\\\'")}', '#{kind}', this.getAttribute('data-content'))\" data-content=\"#{escaped_content}\" title=\"View content\">{ }</button>"
      html << "<button class=\"gallery-expand-btn\" onclick=\"showcaseGallery.expandItem('#{item_id}', '#{ensure_utf8(title).gsub("'", "\\\\'")}')\" title=\"Expand to fullscreen\">⛶</button>"
      html << "</div>"
      html << "</div>"
      html << "<div class=\"gallery-item-image\">"
      html << ensure_utf8(svg_content)
      html << "</div>"
      html << "</div>"
    end

    html << "</div>"
    html << get_gallery_modal
    html << get_content_modal

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

