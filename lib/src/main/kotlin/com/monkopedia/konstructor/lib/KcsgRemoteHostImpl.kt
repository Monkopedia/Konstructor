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
import java.nio.file.Path

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
        return STL.file(file.toPath())
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
        return stlLocation.toPath()
    }

    override fun storeCached(hash: String, csg: CSG) {
        val path = dispatchThread.blockForSuspension {
            hostService.storeCached(hash)
        }
        val file = File(path)
        file.writeText(csg.toStlString())
    }
}
