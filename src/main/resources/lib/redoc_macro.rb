require 'asciidoctor/extensions'
require 'json'
require 'securerandom'

class RedocBlockMacro < Asciidoctor::Extensions::BlockMacroProcessor
  use_dsl
  named :redocly

  def process(parent, target, attrs)

    title = attrs['title'] || 'API Documentation'
    disable_search = attrs.fetch('disableSearch', 'false').to_s.downcase == 'true'
    hide_hostname = attrs.fetch('hideHostname', 'true').to_s.downcase == 'true'
    required_props_first = attrs.fetch('requiredPropsFirst', 'true').to_s.downcase == 'true'
    primary_color = attrs.fetch('primaryColor', '#32329f')
    paths_script = attrs['paths'].to_s
    paths_script_js = paths_script.to_json

    config_options = {
      pageTitle: title,
      disableSearch: disable_search,
      hideHostname: hide_hostname,
      requiredPropsFirst: required_props_first,
      theme: {
        colors: {
          primary: {
            main: primary_color
          }
        }
      }
    }

    config_options_js = config_options.to_json
    backend = parent.document.attr('backend') || 'html5'

    unless backend.downcase == 'html5'
      return create_paragraph parent, %(OpenAPI spec: #{target}), {}
    end

    container_id = "redoc-container-#{SecureRandom.hex(6)}"
    spec_url_js = target.to_s.to_json

    # Optional attributes:
    # redocly::https://url/openapi.yaml[token=YOUR_TOKEN,primaryColor=#32329f,disableSearch=false]
    token = attrs['token']
    primary_color = attrs.fetch('primaryColor', '#32329f')
    disable_search = attrs.fetch('disableSearch', 'false').to_s.downcase == 'true'

    auth_header_line =
      if token && !token.strip.empty?
        %('Authorization': 'Bearer #{escape_javascript(token)}',)
      else
        ''
      end

    html = <<~HTML
      <div id="#{container_id}"></div>

      <script>
        const specUrl = #{spec_url_js};
        const pathsScript = #{paths_script_js};
        
        fetch(specUrl, {
          method: 'GET',
          headers: {
            'Accept': 'application/json, application/yaml, application/x-yaml, text/yaml, text/x-yaml, text/plain',
            #{auth_header_line}
          }
        })
        .then(async response => {
          if (!response.ok) {
            throw new Error(`HTTP error! Status: ${response.status}`);
          }

          const contentType = (response.headers.get('content-type') || '').toLowerCase();
          const isYamlByUrl = /\\.ya?ml($|\\?)/i.test(specUrl);
          const isYamlByContentType =
            contentType.includes('yaml') ||
            contentType.includes('x-yaml') ||
            contentType.includes('text/plain');

          if (isYamlByUrl || isYamlByContentType) {
            if (typeof window.jsyaml === 'undefined' || typeof window.jsyaml.load !== 'function') {
              throw new Error('YAML spec detected, but js-yaml is not loaded (window.jsyaml.load missing).');
            }
            const yamlText = await response.text();
            return window.jsyaml.load(yamlText);
          }

          return response.json();
        })
        .then(spec => {
          if (!spec.info) spec.info = {};
          spec.info.title = "#{title}";
          console.log(pathsScript);
          if (pathsScript && pathsScript.trim().length > 0) {
            try {
              new Function('spec', pathsScript)(spec);
            } catch (e) {
              console.error('Failed to apply paths script:', e);
            }
          }
          console.log(spec.paths)
          Redoc.init(
            spec,
            #{config_options_js},
            document.getElementById('#{container_id}')
          );
        })
        .catch(error => {
          console.error('Failed to load or render the OpenAPI specification:', error);
        });
      </script>
    HTML

    create_pass_block parent, html, attrs, subs: nil
  end

  private

  def escape_javascript(value)
    value.to_s.gsub('\\', '\\\\\\').gsub("'", "\\\\'")
  end
end

Asciidoctor::Extensions.register do
  block_macro RedocBlockMacro
end