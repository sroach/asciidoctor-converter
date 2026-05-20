package gy.roach.asciidoctor.tabs

import org.asciidoctor.Asciidoctor
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object AsciidoctorTabsLoader {

    private val filesToCopy = listOf(
        "/lib/asciidoctor-tabs.rb",
        "/lib/asciidoctor/tabs.rb",
        "/lib/asciidoctor/tabs/block.rb",
        "/lib/asciidoctor/tabs/docinfo.rb",
        "/lib/asciidoctor/tabs/extensions.rb",
        "/lib/asciidoctor/tabs/version.rb",
        "/data/css/tabs.css",
        "/data/js/tabs.js"
    )

    fun load(asciidoctor: Asciidoctor): Path {
        val root = Files.createTempDirectory("asciidoctor-tabs-")
        val rootFile = root.toFile().apply { deleteOnExit() }

        filesToCopy.forEach { resourcePath ->
            val input = requireNotNull(AsciidoctorTabsLoader::class.java.getResourceAsStream(resourcePath)) {
                "Missing resource: $resourcePath"
            }
            input.use { stream ->
                val target = root.resolve(resourcePath.removePrefix("/"))
                Files.createDirectories(target.parent)
                Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING)
                target.toFile().deleteOnExit()
            }
        }

        val libPath = root.resolve("lib").toAbsolutePath().toString().replace("\\", "/")
        val rubyBootstrap = """
            ${'$'}LOAD_PATH.unshift('$libPath') unless ${'$'}LOAD_PATH.include?('$libPath')
            require 'asciidoctor-tabs'
        """.trimIndent()

        ByteArrayInputStream(rubyBootstrap.toByteArray(StandardCharsets.UTF_8)).use { script ->
            asciidoctor.rubyExtensionRegistry().loadClass(script)
        }

        return root
    }
}
