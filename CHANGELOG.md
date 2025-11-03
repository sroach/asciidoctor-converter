# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [2025.1.0] - 2025-11-03

### Added
- Showcase gallery support with rich interactions:
  - New DocOps Showcase block processor for gallery-style content, including AsciiDoc and HTML renderers ([0072892](https://github.com/sroach/asciidoctor-converter/commit/007289223a1a4ca2b0820c8a9f7fec8d184b6a2b)).
  - Content modal with copy-to-clipboard and keyboard navigation for gallery items ([b372f78](https://github.com/sroach/asciidoctor-converter/commit/b372f78f20de78f89a616e04193dfbfa42e11b24)).
  - Inline SVG fetching for gallery items and more interactive gallery modals ([0d53c51](https://github.com/sroach/asciidoctor-converter/commit/0d53c51f1e7984ba1b21e683be56b6dc53e9bc97), [02cf4a5](https://github.com/sroach/asciidoctor-converter/commit/02cf4a538daafc5b855460a1ed64391e793d7615)).
- Fullscreen modal with zoom and pan controls for Mermaid diagrams using svg-pan-zoom ([65dbda3](https://github.com/sroach/asciidoctor-converter/commit/65dbda312e95d6ce03a7766a9af29d98fabd78a4)).

### Changed
- Consolidated showcase-related CSS and JavaScript into docinfo.html for easier maintenance ([0d53c51](https://github.com/sroach/asciidoctor-converter/commit/0d53c51f1e7984ba1b21e683be56b6dc53e9bc97), [02cf4a5](https://github.com/sroach/asciidoctor-converter/commit/02cf4a538daafc5b855460a1ed64391e793d7615)).
- Updated Spring Boot parent and related dependencies as part of gallery and diagram enhancements ([0072892](https://github.com/sroach/asciidoctor-converter/commit/007289223a1a4ca2b0820c8a9f7fec8d184b6a2b), [65dbda3](https://github.com/sroach/asciidoctor-converter/commit/65dbda312e95d6ce03a7766a9af29d98fabd78a4)).
- Improved file handling by setting the base directory in AsciiDoctorConverter ([65dbda3](https://github.com/sroach/asciidoctor-converter/commit/65dbda312e95d6ce03a7766a9af29d98fabd78a4)).


