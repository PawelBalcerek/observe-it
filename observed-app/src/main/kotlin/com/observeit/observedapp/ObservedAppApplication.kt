package com.observeit.observedapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ObservedAppApplication

fun main(args: Array<String>) {
    runApplication<ObservedAppApplication>(*args)
}
