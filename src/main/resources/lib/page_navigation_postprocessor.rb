require 'asciidoctor/extensions'

class PageNavigationPostprocessor < Asciidoctor::Extensions::Postprocessor
  def process document, output
    # Only process HTML backends
    backend = document.attr('backend') || ''
    return output unless backend.downcase == 'html5' || backend.downcase == 'html'

    puts "🔍 PageNavigationPostprocessor: Processing document: #{document.attributes['docfile'] || 'unknown'}"
    puts "🔍 All document attributes:"
    document.attributes.each do |key, value|
      if key.include?('page') || key.include?('prev') || key.include?('next') || key.include?('navigation')
        puts "    #{key} = #{value}"
      end
    end
    puts "🔍 Raw attributes check:"
    puts "    page-navigation: #{document.attr?('page-navigation')} (#{document.attr('page-navigation')})"
    puts "    prev-page-title: #{document.attr('prev-page-title')}"
    puts "    next-page-title: #{document.attr('next-page-title')}"

    if document.attr?('page-navigation') && document.attr('page-navigation').to_s.downcase == 'true'
      puts "✅ page-navigation attribute found and enabled"

      prev_title = document.attr('prev-page-title')
      prev_url = document.attr('prev-page-url')
      next_title = document.attr('next-page-title')
      next_url = document.attr('next-page-url')

      puts "🔗 Navigation data: prev=[#{prev_title}|#{prev_url}] next=[#{next_title}|#{next_url}]"

      navigation_html = build_navigation_html(prev_title, prev_url, next_title, next_url)

      # Insert navigation before the closing body tag
      if output.include?('</body>')
        output = output.sub(/<\/body>/, "#{navigation_html}</body>")
        puts "✅ Navigation HTML added before </body> tag"
      else
        # Fallback: append to end of content
        puts "⚠️ No </body> tag found, appending to end"
        output += navigation_html
      end
    else
      puts "❌ page-navigation attribute not found or not set to true"
      puts "    Available attributes containing 'page': #{document.attributes.keys.select { |k| k.to_s.include?('page') }}"
    end

    output
  end

  private

  def build_navigation_html(prev_title, prev_url, next_title, next_url)
    nav_items = []

    if prev_title && prev_url && !prev_title.empty? && !prev_url.empty?
      nav_items << %(<a href="#{prev_url}" class="nav-prev">&larr; #{prev_title}</a>)
    else
      nav_items << %(<span class="nav-prev-disabled">&larr; Previous</span>)
    end

    if next_title && next_url && !next_title.empty? && !next_url.empty?
      nav_items << %(<a href="#{next_url}" class="nav-next">#{next_title} &rarr;</a>)
    else
      nav_items << %(<span class="nav-next-disabled">Next &rarr;</span>)
    end

    navigation_html = <<~HTML
      <nav class="page-navigation">
        <div class="nav-container">
          #{nav_items.join("\n          ")}
        </div>
      </nav>
    HTML

    puts "🎨 Generated navigation HTML (#{navigation_html.length} chars)"
    navigation_html
  end
end

Asciidoctor::Extensions.register do
  postprocessor PageNavigationPostprocessor
  puts "📝 PageNavigationPostprocessor registered"
end