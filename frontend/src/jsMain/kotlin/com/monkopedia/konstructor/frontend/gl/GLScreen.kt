package com.monkopedia.konstructor.frontend.gl

import com.monkopedia.konstructor.frontend.koin.KonstructionScope
import com.monkopedia.konstructor.frontend.model.KonstructionModel
import com.monkopedia.konstructor.frontend.utils.useCollected
import org.koin.core.component.get
import react.FC
import react.Props

external interface GLScreenProps : Props {
    var konstructionScope: KonstructionScope
}

val GLScreen = FC<GLScreenProps> { props ->
    val model = props.konstructionScope.get<KonstructionModel>()
    val konstruction = model.konstruction.useCollected()
    val konstructionPath = model.rendered.useCollected()
    GLComponent {
        this.konstruction = konstruction
        this.konstructionPath = konstructionPath
    }
}

