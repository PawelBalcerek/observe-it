package com.observeit.trafficgenerator.traffic.domain

fun interface StepPacer {
    fun pace()
}

object NoOpStepPacer : StepPacer {
    override fun pace() = Unit
}
