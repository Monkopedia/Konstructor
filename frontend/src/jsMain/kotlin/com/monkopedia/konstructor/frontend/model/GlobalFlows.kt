package com.monkopedia.konstructor.frontend.model

import com.monkopedia.konstructor.frontend.utils.MutablePersistentFlow

object GlobalFlows {
    val currentWorkspace: MutablePersistentFlow<String?> by lazy {
        MutablePersistentFlow.optionalString("workspace")
    }
    val currentKonstruction: MutablePersistentFlow<String?> by lazy {
        MutablePersistentFlow.optionalString("konstruction")
    }
}