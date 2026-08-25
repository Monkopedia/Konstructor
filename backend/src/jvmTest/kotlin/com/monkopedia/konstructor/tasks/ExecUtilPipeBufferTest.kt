/*
 * Copyright 2022 Jason Monk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.monkopedia.konstructor.tasks

import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * [ExecUtil.executeAndWait] used to call `waitFor()` with the child's stdout and stderr
 * pipes undrained, so any command emitting more than the OS pipe buffer (65536 bytes on
 * Linux) blocked in `write()` forever and the call never returned (konstructor#125).
 *
 * The volumes here straddle that buffer: [BELOW_PIPE_BUFFER] fits and returned even
 * before the fix, [ABOVE_PIPE_BUFFER] does not. They are deliberately small — the point
 * is crossing the threshold, not stressing memory — and none of the output is ever
 * printed, only measured.
 */
class ExecUtilPipeBufferTest {

    @Test
    fun testOutputBelowPipeBufferIsReturned() {
        val result = executeWithin(TIMEOUT_MS, emit(BELOW_PIPE_BUFFER, toStdErr = false))
        assertEquals(BELOW_PIPE_BUFFER, result.stdOut.length)
        assertEquals("", result.stdErr)
        assertEquals(0, result.returnCode)
    }

    @Test
    fun testOutputAbovePipeBufferIsReturned() {
        val result = executeWithin(TIMEOUT_MS, emit(ABOVE_PIPE_BUFFER, toStdErr = false))
        assertEquals(ABOVE_PIPE_BUFFER, result.stdOut.length)
        assertEquals("", result.stdErr)
        assertEquals(0, result.returnCode)
    }

    @Test
    fun testErrorOutputAbovePipeBufferIsReturned() {
        val result = executeWithin(TIMEOUT_MS, emit(ABOVE_PIPE_BUFFER, toStdErr = true))
        assertEquals("", result.stdOut)
        assertEquals(ABOVE_PIPE_BUFFER, result.stdErr.length)
        assertEquals(0, result.returnCode)
    }

    @Test
    fun testBothStreamsAbovePipeBufferStayDistinct() {
        val command = "${emit(ABOVE_PIPE_BUFFER, toStdErr = false)}; " +
            "${emit(ABOVE_PIPE_BUFFER, toStdErr = true)}; exit 3"
        val result = executeWithin(TIMEOUT_MS, command)
        assertEquals(ABOVE_PIPE_BUFFER, result.stdOut.length)
        assertEquals(ABOVE_PIPE_BUFFER, result.stdErr.length)
        assertEquals(3, result.returnCode)
    }

    companion object {
        private const val BELOW_PIPE_BUFFER = 65000
        private const val ABOVE_PIPE_BUFFER = 66000
        private const val TIMEOUT_MS = 30_000L

        /** A command writing exactly [bytes] printable characters to one stream. */
        private fun emit(bytes: Int, toStdErr: Boolean) =
            "head -c $bytes /dev/zero | tr '\\0' 'x'" + if (toStdErr) " 1>&2" else ""

        /**
         * Runs [command] on a throwaway thread so a deadlocked
         * [ExecUtil.executeAndWait] fails this test instead of hanging the whole suite.
         */
        private fun executeWithin(timeoutMs: Long, command: String): ExecUtil.ExecResult {
            var result: ExecUtil.ExecResult? = null
            var failure: Throwable? = null
            thread(isDaemon = true, name = "exec-under-test") {
                try {
                    result = ExecUtil.executeAndWait(command)
                } catch (t: Throwable) {
                    failure = t
                }
            }.join(timeoutMs)
            failure?.let { throw it }
            return result
                ?: fail("executeAndWait did not return within ${timeoutMs}ms for: $command")
        }
    }
}
