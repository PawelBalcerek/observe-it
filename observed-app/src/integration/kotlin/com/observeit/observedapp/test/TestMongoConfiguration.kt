package com.observeit.observedapp.test

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mongodb.MongoDBContainer

@TestConfiguration(proxyBeanMethods = false)
class TestMongoConfiguration {

    @Bean
    @ServiceConnection
    fun mongoDbContainer(): MongoDBContainer = CONTAINER

    private companion object {
        val CONTAINER: MongoDBContainer = MongoDBContainer("mongo:8.0.38").apply { start() }
    }
}
