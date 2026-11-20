package com.observeit.e2e.fixtures

import java.util.concurrent.TimeUnit.SECONDS

class DockerClientFixture {
    fun imageExists(imageReference: String): Boolean =
        runCommand(listOf("docker", "image", "inspect", imageReference)).exitCode == 0

    fun containerHealthStatus(containerName: String): String = inspectFormat(containerName, "{{.State.Health.Status}}")

    fun containerStateStatus(containerName: String): String = inspectFormat(containerName, "{{.State.Status}}")

    fun containerExitCode(containerName: String): Int? =
        inspectFormat(containerName, "{{.State.ExitCode}}").toIntOrNull()

    fun containerOomKilled(containerName: String): Boolean? =
        inspectFormat(containerName, "{{.State.OOMKilled}}").toBooleanStrictOrNull()

    private fun inspectFormat(containerName: String, format: String): String {
        val result = runCommand(listOf("docker", "inspect", containerName, "--format", format))
        return if (result.exitCode == 0) result.stdout.trim() else ""
    }

    private fun runCommand(command: List<String>): CommandResult {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return CommandResult(exitCode = -1, stdout = "")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return CommandResult(exitCode = process.exitValue(), stdout = output)
    }

    private data class CommandResult(val exitCode: Int, val stdout: String)

    private companion object {
        const val COMMAND_TIMEOUT_SECONDS = 30L
    }
}
