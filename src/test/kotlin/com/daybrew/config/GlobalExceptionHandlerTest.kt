package com.daybrew.config

import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `handleNotFound returns 404 ProblemDetail`() {
        val detail = handler.handleNotFound(NoSuchElementException("Idea not found: 99"))

        assertThat(detail.status).isEqualTo(HttpStatus.NOT_FOUND.value())
        assertThat(detail.detail).contains("not found")
    }

    @Test
    fun `handleBadRequest returns 400 ProblemDetail`() {
        val detail = handler.handleBadRequest(IllegalArgumentException("Invalid fingerprint"))

        assertThat(detail.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
        assertThat(detail.detail).contains("Invalid")
    }

    @Test
    fun `handleMissingHeader returns 400 ProblemDetail`() {
        val ex = MissingRequestHeaderException("X-Fingerprint", mockk(relaxed = true))

        val detail = handler.handleMissingHeader(ex)

        assertThat(detail.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
        assertThat(detail.detail).contains("missing")
    }

    @Test
    fun `handleUnexpected returns 500 ProblemDetail with correlationId`() {
        val detail = handler.handleUnexpected(RuntimeException("something went wrong"))

        assertThat(detail.status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value())
        assertThat(detail.properties).isNotNull
        assertThat(detail.properties!!).containsKey("correlationId")
    }

    @Test
    fun `handleUnexpected assigns unique correlationId each call`() {
        val first = handler.handleUnexpected(RuntimeException("err1"))
        val second = handler.handleUnexpected(RuntimeException("err2"))

        val id1 = first.properties!!["correlationId"]
        val id2 = second.properties!!["correlationId"]
        assertThat(id1).isNotEqualTo(id2)
    }
}
