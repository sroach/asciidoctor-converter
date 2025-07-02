package gy.roach.asciidoctor.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "github")
data class GitHubConfig(
    var staging: StagingConfig = StagingConfig(),
    var web: WebConfig = WebConfig(),
    var disable: DisableConfig = DisableConfig()
) {
    data class StagingConfig(
        var directory: String = ""
    )

    data class WebConfig(
        var directory: String = ""
    )

    data class DisableConfig(
        var ssl: SslConfig = SslConfig()
    ) {
        data class SslConfig(
            var validation: Boolean = false
        )
    }

    // Convenience properties for backward compatibility
    val stagingBaseDir: String
        get() = staging.directory

    val webBaseDir: String
        get() = web.directory

    val disableSslValidation: Boolean
        get() = disable.ssl.validation
}
