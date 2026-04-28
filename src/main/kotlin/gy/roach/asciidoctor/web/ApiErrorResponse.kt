package gy.roach.asciidoctor.web

data class ApiErrorResponse(
    val error: String,
    val message: String,
    val field: String? = null
)
