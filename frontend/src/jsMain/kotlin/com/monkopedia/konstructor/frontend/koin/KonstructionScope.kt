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
package com.monkopedia.konstructor.frontend.koin

import com.monkopedia.konstructor.frontend.model.KonstructionModel
import com.monkopedia.konstructor.frontend.model.RenderModel
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.ScopeDSL

class KonstructionScope(private val workspaceScope: WorkspaceScope, val konstructionId: String) :
    CoroutineKoinScope() {

    override fun onScopeCreated(scope: Scope) {
        scope.linkTo(workspaceScope.scope)
    }

    companion object {
        val konstructionId = named("konstruction.id")

        fun ScopeDSL.initScope() {
            scoped {
                get<KonstructionScope>().coroutineScope
            }
            scoped(konstructionId) {
                get<KonstructionScope>().konstructionId
            }
            scoped {
                KonstructionModel(get(), get(), get(konstructionId), get(),)
            }
            scoped {
                RenderModel(get(), get())
            }
        }
    }
}
