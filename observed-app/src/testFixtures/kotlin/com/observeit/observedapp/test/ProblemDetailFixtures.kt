package com.observeit.observedapp.test

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.ProblemDetail

private const val ERRORS_KEY = "errors"
private const val VALIDATION_ERROR_DETAIL = "Request validation failed"

@Suppress("UNCHECKED_CAST")
private val ProblemDetail.errors: List<String>
    get() = (properties?.get(ERRORS_KEY) as? List<String>) ?: emptyList()

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
