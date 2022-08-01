package com.monkopedia.konstructor.frontend

import react.StateInstance

class StateContext<T : Any?>(val delegate: StateInstance<T>) {
    var state: T
        inline get() = delegate.component1()
        set(value) {
            delegate.component2().invoke(value)
        }
}
