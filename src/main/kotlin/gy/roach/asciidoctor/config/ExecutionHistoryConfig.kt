package gy.roach.asciidoctor.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.execution-history")
data class ExecutionHistoryConfig(
    var maxSize: Int = 10
)