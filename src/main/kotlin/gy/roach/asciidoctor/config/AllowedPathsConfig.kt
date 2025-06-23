package gy.roach.asciidoctor.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.security")
data class AllowedPathsConfig(var allowedBasePaths: List<String> = listOf())