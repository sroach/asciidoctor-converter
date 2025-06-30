package gy.roach.asciidoctor.tabs

import org.asciidoctor.ast.Document
import org.asciidoctor.extension.DocinfoProcessor

class BlockSwitchDocinfoProcessor: DocinfoProcessor() {

    override fun process(document: Document): String {
        return BlockSwitchDocinfo().header()
    }
}