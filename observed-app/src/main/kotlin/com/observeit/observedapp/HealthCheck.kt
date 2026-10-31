package com.observeit.observedapp

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

object HealthCheck {
    private const val DEFAULT_PORT = 8080
    private const val HTTP_OK = 200
    private const val TIMEOUT_SECONDS = 3L

    fun check(url: String): Boolean = try {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build()
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        response.statusCode() == HTTP_OK && response.body().contains("\"UP\"")
    } catch (_: Exception) {
        false
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val url = if (args.firstOrNull()?.startsWith("http") == true) {
            args[0]
        } else {
            val port = args.firstOrNull()?.toIntOrNull() ?: DEFAULT_PORT
            "http://127.0.0.1:$port/actuator/health"
        }
        if (check(url)) {
            exitProcess(0)
        } else {
            exitProcess(1)
        }
    }
}
