package com.observeit.trafficgenerator

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class TrafficGeneratorHealthTest : IntegrationTest() {

    @Test
    fun `health endpoint returns UP status with HTTP 200`() {
        // given
        val path = "/actuator/health"
        val jsonMapper = JsonMapper()

        // when
        val response = restTestClient.get()
            .uri(path)
            .exchange()

        // then
        response.expectStatus().isOk

        // and
        val body = response.expectBody(String::class.java).returnResult().responseBody
        val json = jsonMapper.readTree(body)
        json.get("status").asString() shouldBe "UP"
    }
}
