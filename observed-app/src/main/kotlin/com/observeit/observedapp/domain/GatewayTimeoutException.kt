package com.observeit.observedapp.domain

abstract class GatewayTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
