package com.observeit.observedapp.test

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.GATEWAY_TIMEOUT
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT
import org.springframework.http.ProblemDetail

private const val ERRORS_KEY = "errors"
private const val VALIDATION_ERROR_DETAIL = "Request validation failed"

@Suppress("UNCHECKED_CAST")
private val ProblemDetail.errors: List<String>
    get() = (properties?.get(ERRORS_KEY) as? List<String>).orEmpty()

fun ProblemDetail.shouldBeValidationError(vararg expectedErrors: String) {
    assertSoftly(this) {
        status shouldBe BAD_REQUEST.value()
        title shouldBe BAD_REQUEST.reasonPhrase
        detail shouldBe VALIDATION_ERROR_DETAIL
        if (expectedErrors.isNotEmpty()) {
            errors shouldContainAll expectedErrors.toList()
        }
    }
}

fun ProblemDetail.shouldBeNotFoundError(expectedDetail: String? = null) {
    assertSoftly(this) {
        status shouldBe NOT_FOUND.value()
        title shouldBe NOT_FOUND.reasonPhrase
        if (expectedDetail != null) {
            detail shouldBe expectedDetail
        }
    }
}

fun ProblemDetail.shouldBeConflictError(expectedDetail: String? = null) {
    assertSoftly(this) {
        status shouldBe CONFLICT.value()
        title shouldBe CONFLICT.reasonPhrase
        if (expectedDetail != null) {
            detail shouldBe expectedDetail
        }
    }
}

fun ProblemDetail.shouldBeUnprocessableEntityError(expectedDetail: String? = null) {
    assertSoftly(this) {
        status shouldBe UNPROCESSABLE_CONTENT.value()
        title shouldBe UNPROCESSABLE_CONTENT.reasonPhrase
        if (expectedDetail != null) {
            detail shouldBe expectedDetail
        }
    }
}

fun ProblemDetail.shouldBeGatewayTimeoutError(expectedDetail: String? = null) {
    assertSoftly(this) {
        status shouldBe GATEWAY_TIMEOUT.value()
        title shouldBe GATEWAY_TIMEOUT.reasonPhrase
        if (expectedDetail != null) {
            detail shouldBe expectedDetail
        }
    }
}
