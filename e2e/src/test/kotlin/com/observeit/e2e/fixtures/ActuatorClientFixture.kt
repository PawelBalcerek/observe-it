package com.observeit.e2e.fixtures

class ActuatorClientFixture(private val http: HttpJsonClient) {
    fun healthStatus(baseUrl: String): String = http.getJson("$baseUrl/actuator/health").path("status").asString("")
}
