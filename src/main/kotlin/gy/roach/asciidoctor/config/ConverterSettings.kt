package gy.roach.asciidoctor.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "converter")
class ConverterSettings (){
    var panelServer: String = ""
    var panelWebserver: String = ""
    var localDebug: Boolean = false

}