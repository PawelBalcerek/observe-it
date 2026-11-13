package com.observeit.trafficgenerator.traffic

import com.observeit.trafficgenerator.test.MetricCapture

internal fun MetricCapture.assertWorkerGauges(targetRate: Double, activeWorkers: Long) {
    assertGaugeValue(
        name = "traffic.target_rate",
        unit = "{order}/s",
        value = targetRate,
    )
    assertGaugeValue(
        name = "traffic.workers.active",
        unit = "{worker}",
        value = activeWorkers,
    )
}
