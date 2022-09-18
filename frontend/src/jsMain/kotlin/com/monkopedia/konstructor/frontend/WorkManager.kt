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
package com.monkopedia.konstructor.frontend

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mui.material.Backdrop
import mui.material.CircularProgress
import react.FC
import react.Props

class WorkManager() {
    lateinit var onWorkingChanged: (Boolean) -> Unit
    private var jobCount = 0

    fun startJob() {
        if (jobCount++ == 0) {
            onWorkingChanged(true)
        }
    }

    fun endJob() {
        if (--jobCount == 0) {
            onWorkingChanged(false)
        }
    }

    inline fun doWork(crossinline work: suspend () -> Unit) {
        GlobalScope.launch {
            startJob()
            try {
                work()
            } finally {
                endJob()
            }
        }
    }
}

external interface WorkProps : Props {
    var isWorking: Boolean
}

val WorkDisplay = FC<WorkProps> { props ->
    Backdrop {
        open = props.isWorking
        CircularProgress()
    }
}
