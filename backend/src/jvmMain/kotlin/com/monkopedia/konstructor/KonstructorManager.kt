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
import java.util.WeakHashMap

class KonstructorManager private constructor(private val config: Config) {
    private val controllers = WeakHashMap<Pair<String, String>, KonstructionController>()

    fun controllerFor(konstruction: Konstruction): KonstructionController {
        return controllerFor(konstruction.workspaceId, konstruction.id)
    }

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
