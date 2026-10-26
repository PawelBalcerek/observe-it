package com.observeit.observedapp.api

import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalControllerAdvice {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(exception: MethodArgumentNotValidException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(
            BAD_REQUEST,
            VALIDATION_ERROR_DETAIL,
        )
        problem.title = BAD_REQUEST.reasonPhrase
        val errors = exception.bindingResult.allErrors.mapNotNull { it.defaultMessage }
        problem.setProperty(ERRORS_KEY, errors)
        return problem
    }

    private companion object {
        const val VALIDATION_ERROR_DETAIL = "Request validation failed"
        const val ERRORS_KEY = "errors"
    }
}
