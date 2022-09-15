package com.monkopedia.konstructor.frontend.gl

import com.monkopedia.konstructor.frontend.koin.KonstructionScope
import com.monkopedia.konstructor.frontend.model.KonstructionModel
import com.monkopedia.konstructor.frontend.model.RenderModel
import com.monkopedia.konstructor.frontend.utils.useCollected
import kotlinx.coroutines.flow.combine
import org.koin.core.component.get
import react.FC
import react.Props

external interface GLScreenProps : Props {
    var konstructionScope: KonstructionScope
}

val GLScreen = FC<GLScreenProps> { props ->
    val model = props.konstructionScope.get<KonstructionModel>()
    val konstruction = model.konstruction.useCollected()
    val konstructionPath = props.konstructionScope.useCollected {
        combine(get<RenderModel>().allTargets, model.rendered) { allTargets, rendered ->
            allTargets.entries.filter { it.value.isEnabled }.mapNotNull {
                it.key to ((rendered[it.key] ?: return@mapNotNull null) to it.value.color)
            }.toMap()
        }
    }
    val reload = model.reload.useCollected(0)
    GLComponent {
        this.konstruction = konstruction
        this.konstructionPath = konstructionPath
        this.reload = reload
    }
}

