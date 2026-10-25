package com.observeit.trafficgenerator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TrafficGeneratorApplication

fun main(args: Array<String>) {
    runApplication<TrafficGeneratorApplication>(*args)
}
