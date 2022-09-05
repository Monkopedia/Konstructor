package com.monkopedia.konstructor.frontend.menu

import com.monkopedia.konstructor.frontend.model.NavigationDialogModel
import react.FC
import react.Props
import react.memo

external interface DialogMenusProps : Props {
    var dialogModel: NavigationDialogModel
}

val DialogMenus = memo(
    FC<DialogMenusProps> { props ->
        createKonstructionDialog {
            this.dialogModel = props.dialogModel
        }
        createWorkspaceDialog {
            this.dialogModel = props.dialogModel
        }
        editKonstructionDialog {
            this.dialogModel = props.dialogModel
        }
        editWorkspaceDialog {
            this.dialogModel = props.dialogModel
        }
    }
) { oldProps, newProps ->
    oldProps.dialogModel === newProps.dialogModel
}
