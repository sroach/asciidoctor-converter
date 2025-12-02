package gy.roach.asciidoctor.actuator

import gy.roach.asciidoctor.service.ConversionContext
import gy.roach.asciidoctor.service.ConversionJob
import gy.roach.asciidoctor.service.ConversionJobService
import gy.roach.asciidoctor.web.ActiveConversion
import gy.roach.asciidoctor.web.ExecutionRecord
import gy.roach.asciidoctor.web.ExecutionSummary
import gy.roach.asciidoctor.web.MainController
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.boot.actuate.endpoint.annotation.Selector
import org.springframework.stereotype.Component

@Component
@Endpoint(id = "conversions") // This sets the path to /actuator/conversions
class ConversionJobEndpoint(
    private val conversionJobService: ConversionJobService,
    private val conversionContext: ConversionContext
) {



    /**
     * Exposes all jobs at: GET /actuator/conversions
     */
    @ReadOperation
    fun getAllJobs(): Map<String, ConversionJob> {

        return conversionJobService.getAllJobs()
    }

    /**
     * Exposes specific job status at: GET /actuator/conversions/{jobId}
     * Also exposes metrics at: GET /actuator/conversions/stats
     */
    @ReadOperation
    fun getJob(@Selector jobId: String): Any? {
        if (jobId == "stats") {
            return mapOf(
                "totalExecutionCount" to conversionContext.totalExecutionCount.get(),
                "activeConversionsCount" to conversionContext.activeConversions.size,
                "activeConversions" to conversionContext.activeConversions,
                "executionHistory" to conversionContext.executionHistory
            )
        }
        return conversionJobService.getJobStatus(jobId)
    }

}

