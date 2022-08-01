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
