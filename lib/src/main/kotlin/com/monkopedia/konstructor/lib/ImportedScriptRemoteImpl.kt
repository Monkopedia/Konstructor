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
package com.monkopedia.konstructor.lib

import com.monkopedia.kcsg.CSG
import com.monkopedia.kcsg.ImportedScript
import com.monkopedia.kcsg.STL
import com.monkopedia.konstructor.lib.TargetStatus.BUILT
import com.monkopedia.konstructor.lib.TargetStatus.ERROR
import java.io.File
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

internal class ImportedScriptRemoteImpl(
    private val script: ScriptService,
    private val dispatchThread: BlockableThread
) : ImportedScript {
    override val exports: Collection<String>
        get() = dispatchThread.blockForSuspension {
            script.listTargets(onlyExports = true).map { it.name }
        }
    override val targets: Collection<String>
        get() = dispatchThread.blockForSuspension {
            script.listTargets(onlyExports = false).map { it.name }
        }

    override fun get(name: String): CSG {
        val path = dispatchThread.blockForSuspension {
            val builder = script.buildTarget(name)
            builder.awaitBuilt().also {
                builder.close()
            }
        }
        val file = File(path)
        if (!file.exists()) error("Could not resolve result of $name properly")
        return STL.file(file.toPath())
    }
}

private suspend fun BuildService.awaitBuilt(): String {
    try {
        statusFlow().filter { it == BUILT || it == ERROR }.first().takeIf { it == ERROR }?.let {
            error("Couldn't build target")
        }
        return getBuiltTarget()
    } finally {
        close()
    }
}
