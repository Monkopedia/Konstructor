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
package com.monkopedia.konstructor

import com.monkopedia.konstructor.common.Konstruction

class KonstructorManager private constructor(private val config: Config) {
    // Strong map, entries never evicted. This used to be a WeakHashMap, which holds its
    // KEYS weakly -- and the key here is `workspaceId to id`, a Pair allocated fresh on
    // every call that nothing outside the map ever references. Every entry was therefore
    // weakly reachable the instant it was inserted, so any GC could drop it even while the
    // controller itself was strongly held by KonstructionServiceImpl. The next lookup then
    // built a second KonstructionControllerImpl with its own contentFileLock/scriptLock,
    // and the script path and service path locked different Mutexes for the same
    // konstruction -- silently, permanently, from the first GC onward (#126).
    //
    // Controller identity is load-bearing precisely because those Mutexes are per-instance
    // fields, so this cache must not evict: adding an eviction policy here reintroduces the
    // same defect. Nothing is scoped to a controller's lifecycle (there is no close/shutdown
    // on KonstructionController), so retaining one per konstruction for the process lifetime
    // is the accepted cost. Matches the strong per-Config caches in PathController and
    // ScriptManager.
    //
    // Mutation is confined to controllerFor below, entirely under `synchronized(controllers)`,
    // which is what makes the get-construct-put sequence atomic.
    private val controllers = mutableMapOf<Pair<String, String>, KonstructionController>()

    fun controllerFor(konstruction: Konstruction): KonstructionController =
        controllerFor(konstruction.workspaceId, konstruction.id)

    fun controllerFor(workspaceId: String, id: String): KonstructionController {
        synchronized(controllers) {
            return controllers.getOrPut(workspaceId to id) {
                KonstructionControllerImpl(config, workspaceId, id)
            }
        }
    }

    companion object : (Config) -> KonstructorManager {
        private val managers = mutableMapOf<Config, KonstructorManager>()

        override fun invoke(config: Config): KonstructorManager {
            synchronized(managers) {
                return managers.getOrPut(config) {
                    KonstructorManager(config)
                }
            }
        }
    }
}
