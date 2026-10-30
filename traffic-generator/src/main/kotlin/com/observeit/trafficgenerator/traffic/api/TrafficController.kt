package com.observeit.trafficgenerator.traffic.api

import com.observeit.trafficgenerator.traffic.domain.TrafficService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/traffic")
class TrafficController(private val trafficService: TrafficService) {

    @GetMapping("/status")
    fun getStatus(): TrafficStatusResponse = TrafficStatusResponse.from(trafficService.getStatus())
}
