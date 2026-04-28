package gy.roach.asciidoctor.web

import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBodyValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiErrorResponse> {
        val first = ex.bindingResult.fieldErrors.firstOrNull()
        val body = ApiErrorResponse(
            error = "VALIDATION_ERROR",
            message = first?.defaultMessage ?: "Request validation failed",
            field = first?.field
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintValidation(ex: ConstraintViolationException): ResponseEntity<ApiErrorResponse> {
        val first = ex.constraintViolations.firstOrNull()
        val body = ApiErrorResponse(
            error = "VALIDATION_ERROR",
            message = first?.message ?: "Constraint violation",
            field = first?.propertyPath?.toString()
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMalformedJson(ex: HttpMessageNotReadableException): ResponseEntity<ApiErrorResponse> {
        val body = ApiErrorResponse(
            error = "BAD_REQUEST",
            message = "Malformed JSON request body",
            field = null
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ApiErrorResponse> {
        val body = ApiErrorResponse(
            error = "VALIDATION_ERROR",
            message = ex.message ?: "Invalid request",
            field = null
        )
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleOther(ex: Exception): ResponseEntity<ApiErrorResponse> {
        val body = ApiErrorResponse(
            error = "INTERNAL_ERROR",
            message = "Unexpected server error",
            field = null
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }
}
