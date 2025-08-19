package gy.roach.asciidoctor.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "converter")
class ConverterSettings {
    var panelServer: String = ""
    var panelWebserver: String = ""
    var localDebug: Boolean = false
    var defaultFormats: List<String> = listOf("html")
    var epubSettings: EpubSettings? = EpubSettings()
}

data class EpubSettings(
    var title: String? = null,
    var authors: List<String> = emptyList(),
    var language: String = "en",
    var coverImagePath: String? = null,
    var tocLevel: Int = 2,
    var validateOutput: Boolean = true,
    var resourceInclusion: ResourceInclusionSettings = ResourceInclusionSettings()
)

data class ResourceInclusionSettings(
    var includeImages: Boolean = true,
    var includeFonts: Boolean = true,
    var includeStylesheets: Boolean = true,
    var maxImageSize: Long = 10_000_000L // 10MB
)