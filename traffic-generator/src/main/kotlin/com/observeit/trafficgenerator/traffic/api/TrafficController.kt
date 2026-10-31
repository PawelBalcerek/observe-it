package com.observeit.trafficgenerator.traffic.api

import com.observeit.trafficgenerator.traffic.domain.CheckoutScenario
import com.observeit.trafficgenerator.traffic.domain.TrafficService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/traffic")
class TrafficController(private val trafficService: TrafficService) {

    @PostMapping("/trigger/{scenario}")
    fun trigger(@PathVariable scenario: CheckoutScenario): TriggerScenarioResponse =
        TriggerScenarioResponse.from(trafficService.trigger(scenario))

    @GetMapping("/status")
    fun getStatus(): TrafficStatusResponse = TrafficStatusResponse.from(trafficService.getStatus())

    @PostMapping("/pause")
    fun pause(): TrafficStatusResponse = TrafficStatusResponse.from(trafficService.pause())

    @PostMapping("/resume")
    fun resume(): TrafficStatusResponse = TrafficStatusResponse.from(trafficService.resume())

    @PostMapping("/rate")
    fun updateRate(@Valid @RequestBody request: UpdateTrafficRateRequest): UpdateTrafficRateResponse =
        UpdateTrafficRateResponse.from(trafficService.updateRate(request.toDomain()))
}
