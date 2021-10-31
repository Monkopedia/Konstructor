package com.monkopedia.konstructor.tasks

import java.io.BufferedReader
import java.io.InputStreamReader

object ExecUtil {

    data class ExecResult(
        val stdOut: BufferedReader,
        val stdErr: BufferedReader,
        val returnCode: Int
    )

    fun executeAndWait(command: String): ExecResult {
        val rt = Runtime.getRuntime()
        println("Executing: $command")
        val commands = arrayOf(
            "bash",
            "-c",
            command
        )
        val proc = rt.exec(commands)
        return ExecResult(
            BufferedReader(InputStreamReader(proc.inputStream)),
            BufferedReader(InputStreamReader(proc.errorStream)),
            proc.waitFor()
        )
    }

    fun executeWithChannel(command: String, ): ExecResult {
        val rt = Runtime.getRuntime()
        println("Executing: $command")
        val commands = arrayOf(
            "bash",
            "-c",
            command
        )
        val proc = rt.exec(commands)
        return ExecResult(
            BufferedReader(InputStreamReader(proc.inputStream)),
            BufferedReader(InputStreamReader(proc.errorStream)),
            proc.waitFor()
        )
    }
}
