package com.monkopedia.konstructor.frontend.menu

import com.monkopedia.konstructor.frontend.WorkManager
import com.monkopedia.konstructor.frontend.model.WorkspaceModel
import com.monkopedia.konstructor.frontend.utils.useCollected
import react.FC
import react.Props

external interface KonstructionMenuProps : Props {
    var workManager: WorkManager
    var workspaceModel: WorkspaceModel
}

val KonstructionMenu = FC<KonstructionMenuProps> { props ->
    val model = props.workspaceModel
    val konstructions = model.availableKonstructions.useCollected()
    val currentKonstruction = model.currentKonstruction.useCollected()

    if (konstructions?.isNotEmpty() == true) {
        KonstructionSelector {
            this.currentKonstruction = currentKonstruction?.id
            this.konstructions = konstructions
            this.onKonstructionSelected = model.onSelectedKonstruction
        }
    }
    if (currentKonstruction != null) {
        editKonstructionButton {
            this.workManager = props.workManager
            this.currentName = currentKonstruction!!.name
            this.onUpdateName = model.onNameUpdated
        }
    }
    createKonstructionButton {
        this.workManager = props.workManager
        this.workspaceModel = props.workspaceModel
        this.onCreateWorkspace = props.workspaceModel.onCreateKonstruction
    }
}
