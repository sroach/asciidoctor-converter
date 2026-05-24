
# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [2026.0.0] - 2026-05-23

### Added
- Tabbed content support for Markdown and AsciiDoc:
  - Integrated `TabsExtension` for Markdown and `Asciidoctor Tabs` for AsciiDoc with modern theming ([a9ddb59](https://github.com/sroach/asciidoctor-converter/commit/a9ddb59c2f81eb04fd8f4e25c3c759f314e015bd), [ecef34a](https://github.com/sroach/asciidoctor-converter/commit/ecef34a85062c345c886d7f8ff01a2966fbbeec6)).
- PDF export capabilities:
  - Added `toPdf` endpoint and integrated `openpdf-html` for high-quality PDF rendering ([6acbcf3](https://github.com/sroach/asciidoctor-converter/commit/6acbcf34db79c62b0650d5735fce3a4369ec279e), [0bd55e9](https://github.com/sroach/asciidoctor-converter/commit/0bd55e966ae8870a9059b4edd437fbdd5ac1b6d9)).
- Wiki format conversion:
  - Added support for converting documents to Jira and Confluence wiki formats ([ce5801b](https://github.com/sroach/asciidoctor-converter/commit/ce5801becfc87c98ad4229f87454b472f1f9369f), [87d3513](https://github.com/sroach/asciidoctor-converter/commit/87d3513f8fbf6a035e92b77bc69e97960eb932b3)).
- Enhanced diagramming features:
  - iOS-themed PlantUML light and dark themes ([d9baee4](https://github.com/sroach/asciidoctor-converter/commit/d9baee4b11d59c81e6ff37ea78d5590f2d856213)).
  - Mermaid v11.15.0 support with iOS-themed variables ([36686b2](https://github.com/sroach/asciidoctor-converter/commit/36686b220b925ebf4019308046b79d8b1a6db35f), [fb6e3a1](https://github.com/sroach/asciidoctor-converter/commit/fb6e3a1b4bf6c4f68efa6b8f39edb761edafdf90)).
- UI and UX improvements:
  - "SOURCE" button and modal for viewing original document source ([81101d1](https://github.com/sroach/asciidoctor-converter/commit/81101d1844bb8f4c0471bc69f0afd52375ce9e46)).
  - Unified modal system with zoom, pan, and print support for SVGs and CSVs ([0a7d03a](https://github.com/sroach/asciidoctor-converter/commit/0a7d03ad98a0a48115819a1028ef15e9e608f8be), [65d8c3e](https://github.com/sroach/asciidoctor-converter/commit/65d8c3e6defa53234c23c4e2a0dc8b3b423aa6db)).
  - Responsive Bento grid layout for galleries ([33e9cea](https://github.com/sroach/asciidoctor-converter/commit/33e9ceaa39247474be76358c6e71b4c550c00907)).
  - New "Guyana Food Blog" theme and favicon support ([c374e29](https://github.com/sroach/asciidoctor-converter/commit/c374e298cd63f103d23075a2230cbe0a0394f08f), [e913c64](https://github.com/sroach/asciidoctor-converter/commit/e913c64988f2eccc2a3a46114a67304b80d710ba)).

### Changed
- Major dependency upgrades:
  - Upgraded Spring Boot to 4.0.6 ([1345542](https://github.com/sroach/asciidoctor-converter/commit/13455422fca91fccd4f24317ce62e9e3f0491b36)).
  - Upgraded Kotlin to 2.3.20.
  - Upgraded JGit to 7.6.0.
- Internal architecture refinements:
  - Streamlined `AsciiDoctorConverter` source file processing and logging ([41f1efb](https://github.com/sroach/asciidoctor-converter/commit/41f1efb90d5fb24f8cf6efcba36490d19b098d6e)).
  - Introduced `withLocalAsciidoctor` utility to optimize resource handling ([737386f](https://github.com/sroach/asciidoctor-converter/commit/737386f9afe2de5ce47b79bc048ef4f6f163fb74)).
  - Optimized asset injection for SVG viewer ([1a31e40](https://github.com/sroach/asciidoctor-converter/commit/1a31e4016c4a413d9b1640579eb9377568d2a6ec)).

### Fixed
- Resolved Mermaid re-rendering issues using mutation-based fixes ([458090e](https://github.com/sroach/asciidoctor-converter/commit/458090e6405c8886caac67e9f3328382b42db581)).
- Improved nested include attribute resolution and propagation ([68374d9](https://github.com/sroach/asciidoctor-converter/commit/68374d935c4e38e7bb9b1db993450ad5f9c0c2e0)).
- Refined temporary file cleanup logic and concurrency controls ([6acbcf3](https://github.com/sroach/asciidoctor-converter/commit/6acbcf34db79c62b0650d5735fce3a4369ec279e), [44176f8](https://github.com/sroach/asciidoctor-converter/commit/44176f81d4c208083d3f87dcdec37d5266a737a5)).
- Pre-collect attributes from nested includes and inject into conversion options so Asciidoctor resolves parameterized includes (e.g. child-{version}-{type}.adoc) even when attributes are defined in preceding includes like _meta.adoc.

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


