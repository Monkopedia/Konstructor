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
