package com.monkopedia.konstructor.lib

import com.monkopedia.kcsg.CSG
import com.monkopedia.kcsg.ImportedScript
import com.monkopedia.kcsg.STL
import com.monkopedia.konstructor.lib.TargetStatus.BUILT
import com.monkopedia.konstructor.lib.TargetStatus.ERROR
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

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
