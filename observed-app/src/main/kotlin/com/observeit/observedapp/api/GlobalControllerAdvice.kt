package com.observeit.observedapp.api

import com.observeit.observedapp.domain.ConflictException
import com.observeit.observedapp.domain.GatewayTimeoutException
import com.observeit.observedapp.domain.NotFoundException
import com.observeit.observedapp.domain.UnprocessableEntityException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.GATEWAY_TIMEOUT
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT
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

    @ExceptionHandler(ConflictException::class)
    fun handleConflictException(exception: ConflictException): ProblemDetail {
        val detail = exception.message ?: CONFLICT.reasonPhrase
        logClientWarning(detail)
        return problemDetail(CONFLICT, detail)
    }

    @ExceptionHandler(UnprocessableEntityException::class)
    fun handleUnprocessableEntityException(exception: UnprocessableEntityException): ProblemDetail {
        val detail = exception.message ?: UNPROCESSABLE_CONTENT.reasonPhrase
        logClientWarning(detail)
        return problemDetail(UNPROCESSABLE_CONTENT, detail)
    }

    @ExceptionHandler(GatewayTimeoutException::class)
    fun handleGatewayTimeoutException(exception: GatewayTimeoutException): ProblemDetail {
        val detail = exception.message ?: GATEWAY_TIMEOUT.reasonPhrase
        logServerError(detail, exception)
        return problemDetail(GATEWAY_TIMEOUT, detail)
    }

    private fun logClientWarning(message: String) {
        logger.atWarn().log(message)
    }

    private fun logServerError(message: String, exception: Throwable) {
        logger.atError().setCause(exception).log(message)
    }

    private fun problemDetail(status: HttpStatus, detail: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail)

    private companion object {
        const val VALIDATION_ERROR_DETAIL = "Request validation failed"
        const val ERRORS_KEY = "errors"
    }
}
