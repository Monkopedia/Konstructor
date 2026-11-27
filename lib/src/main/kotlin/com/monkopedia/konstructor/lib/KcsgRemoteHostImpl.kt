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
import com.monkopedia.kcsg.KcsgHost
import com.monkopedia.kcsg.STL
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.io.files.Path

internal class KcsgRemoteHostImpl(
    private val hostService: HostService,
    private val dispatchThread: BlockableThread
) : KcsgHost {

    override val supportsCaching: Boolean
        get() = dispatchThread.blockForSuspension {
            hostService.supportsCaching()
        }

    override fun checkCached(hash: String): CSG? {
        val path = dispatchThread.blockForSuspension {
            hostService.checkCached(hash)
        } ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return STL.file(Path(file.absolutePath))
    }

    override fun findScript(csgsName: String): ImportedScript {
        val script = dispatchThread.blockForSuspension {
            hostService.findScript(csgsName)
        }
        return ImportedScriptRemoteImpl(script, dispatchThread)
    }

    override fun findStl(stlName: String): Path {
        val path = dispatchThread.blockForSuspension {
            hostService.findStl(stlName)
        } ?: error("Cannot resolve STL $stlName")
        val f = File(path)
        if (!f.exists()) error("Cannot resolve STL $stlName")
        val stlLocation = File(f.parentFile, f.nameWithoutExtension + ".stl")
        if (stlLocation.exists()) {
            stlLocation.delete()
        }
        f.copyTo(stlLocation)
        System.err.println("Loading stl $stlName $stlLocation")
        return Path(stlLocation.absolutePath)
    }

    override fun stlVersion(stlName: String): String = dispatchThread.blockForSuspension {
        hostService.stlVersion(stlName)
    } ?: ""

    override fun storeCached(hash: String, csg: CSG) {
        val path = dispatchThread.blockForSuspension {
            hostService.storeCached(hash)
        }
        File(path).writeAtomically { it.write(csg.toStlString().toByteArray()) }
    }
}

/**
 * Write this file so it is either absent, its previous content, or complete — never a prefix.
 *
 * The render cache is keyed only by [exists][File.exists], so an in-place write killed midway
 * left a truncated `<hash>.stl` that every later build served as the cached geometry (#166).
 * [write] goes to a uniquely named sibling `.tmp` (unique so concurrent writers of the same
 * hash cannot collide), which is then moved into place with [ATOMIC_MOVE]; a sibling keeps the
 * move within one filesystem. A write killed midway leaves at most a stray `.tmp`, which
 * nothing reads: lookups ask for the exact `<hash>.stl` name.
 */
internal fun File.writeAtomically(write: (OutputStream) -> Unit) {
    val tmp = File.createTempFile("$name.", ".tmp", parentFile)
    try {
        tmp.outputStream().use(write)
        Files.move(tmp.toPath(), toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    } catch (t: Throwable) {
        tmp.delete()
        throw t
    }
}
