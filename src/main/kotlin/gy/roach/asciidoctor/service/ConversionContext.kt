package gy.roach.asciidoctor.service

import gy.roach.asciidoctor.web.ActiveConversion
import gy.roach.asciidoctor.web.ExecutionRecord
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@Component
class ConversionContext {
    // Thread-safe list for history
    val executionHistory: MutableList<ExecutionRecord> = CopyOnWriteArrayList()
    
    // Thread-safe map for active conversions
    val activeConversions: MutableMap<String, ActiveConversion> = ConcurrentHashMap()
    
    // Atomic counter for totals
    val totalExecutionCount = AtomicInteger(0)
}
