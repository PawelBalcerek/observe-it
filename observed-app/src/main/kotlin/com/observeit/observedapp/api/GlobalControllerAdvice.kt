package com.observeit.observedapp.api

import com.observeit.observedapp.domain.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalControllerAdvice {

    private val logger = LoggerFactory.getLogger(GlobalControllerAdvice::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(exception: MethodArgumentNotValidException): ProblemDetail {
        val errors = exception.bindingResult.allErrors.mapNotNull { it.defaultMessage }
        logClientWarning("Request validation failed: $errors")
        val problem = problemDetail(BAD_REQUEST, VALIDATION_ERROR_DETAIL)
        problem.setProperty(ERRORS_KEY, errors)
        return problem
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFoundException(exception: NotFoundException): ProblemDetail {
        val detail = exception.message ?: NOT_FOUND.reasonPhrase
        logClientWarning(detail)
        return problemDetail(NOT_FOUND, detail)
    }

    private fun logClientWarning(message: String) {
        logger.atWarn().log(message)
    }

    private fun problemDetail(status: HttpStatus, detail: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            title = status.reasonPhrase
        }

    private companion object {
        const val VALIDATION_ERROR_DETAIL = "Request validation failed"
        const val ERRORS_KEY = "errors"
    }
}
