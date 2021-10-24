package com.monkopedia.konstructor.frontend

import com.ccfraser.muirwik.components.mBackdrop
import com.ccfraser.muirwik.components.mCircularProgress
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import react.Props
import react.RBuilder
import react.RComponent
import react.State

class WorkManager(private val onWorkingChanged: (Boolean) -> Unit) {

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

class WorkDisplay : RComponent<WorkProps, State>() {
    override fun RBuilder.render() {
        mBackdrop(open = props.isWorking) {
            mCircularProgress {
            }
        }
    }
}
