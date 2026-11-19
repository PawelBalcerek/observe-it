package com.observeit.e2e.config

object E2eConfig {
    val imageTag: String =
        System.getenv("OBSERVE_IT_IMAGE_TAG")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("observeIt.projectVersion")?.takeIf { it.isNotBlank() }
            ?: "0.4.0"

    val imagePrefix: String =
        System.getenv("OBSERVE_IT_IMAGE_PREFIX")?.takeIf { it.isNotBlank() }
            ?: "ghcr.io/pawelbalcerek/observe-it"

    const val OBSERVED_APP_BASE_URL: String = "http://localhost:8080"
    const val TRAFFIC_GENERATOR_BASE_URL: String = "http://localhost:8081"
    const val OPENSEARCH_BASE_URL: String = "http://localhost:9200"
    const val DASHBOARDS_BASE_URL: String = "http://localhost:5601"
}
