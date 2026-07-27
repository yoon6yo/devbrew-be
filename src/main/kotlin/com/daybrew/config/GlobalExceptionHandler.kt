package com.daybrew.config

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ProblemDetail {
        log.warn("Resource not found: ${ex.message}")
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found")
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ProblemDetail {
        log.warn("Invalid request: ${ex.message}")
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request")
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException): ProblemDetail {
        log.warn("Required header missing: ${ex.headerName}")
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Required header missing")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val fields = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation failed: $fields")
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, fields)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        val correlationId = UUID.randomUUID().toString()
        log.error("Unhandled exception [correlationId=$correlationId]", ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error")
            .apply { setProperty("correlationId", correlationId) }
    }
}
