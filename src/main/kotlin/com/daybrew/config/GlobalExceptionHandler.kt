package com.daybrew.config

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ProblemDetail {
        log.error("Resource not found", ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found")
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ProblemDetail {
        log.error("Invalid request", ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request")
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException): ProblemDetail {
        log.error("Required header missing: ${ex.headerName}", ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Required header missing")
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        val correlationId = UUID.randomUUID().toString()
        log.error("Unhandled exception [correlationId=$correlationId]", ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error")
            .apply { setProperty("correlationId", correlationId) }
    }
}
