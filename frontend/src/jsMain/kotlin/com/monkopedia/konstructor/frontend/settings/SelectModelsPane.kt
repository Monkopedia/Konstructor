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
package com.monkopedia.konstructor.frontend.settings

import com.monkopedia.konstructor.frontend.WorkManager
import com.monkopedia.konstructor.frontend.koin.KonstructionScope
import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.model.KonstructionModel
import com.monkopedia.konstructor.frontend.model.RenderModel
import com.monkopedia.konstructor.frontend.model.RenderModel.DisplayTarget
import com.monkopedia.konstructor.frontend.utils.buildExt
import com.monkopedia.konstructor.frontend.utils.useCollected
import emotion.react.css
import kotlinx.browser.window
import mui.icons.material.Colorize
import mui.icons.material.Download
import mui.material.Box
import mui.material.IconButton
import mui.material.ListItem
import mui.material.ListItemButton
import mui.material.ListItemText
import mui.material.Popover
import mui.material.PopoverOrigin
import mui.material.Switch
import mui.material.SwitchBaseEdge
import mui.material.SwitchColor.Companion.primary
import mui.material.Typography
import mui.material.styles.TypographyVariant.Companion.subtitle1
import mui.system.sx
import org.koin.core.component.get
import react.FC
import react.Props
import react.create
import react.dom.aria.ariaLabel
import react.dom.html.ReactHTML.div
import react.memo
import react.useState
import web.cssom.Auto
import web.cssom.Border
import web.cssom.Color
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.Length
import web.cssom.LineStyle
import web.cssom.Overflow
import web.cssom.number
import web.cssom.px
import web.html.HTMLButtonElement

external interface SelectModelsProps : Props {
    var workManager: WorkManager
}

val SelectModelsPane = memo(
    FC<SelectModelsProps> { props ->
        val scope = RootScope.scopeTracker.konstruction.useCollected() ?: return@FC
        val renderModel = scope.get<RenderModel>()
        ScopedSelectModelsPane {
            this.workManager = props.workManager
            this.scope = scope
            this.renderModel = renderModel
        }
    }
) { _, _ ->
    true
}

external interface ScopedSelectModelsProps : Props {
    var workManager: WorkManager
    var scope: KonstructionScope
    var renderModel: RenderModel
}

val ScopedSelectModelsPane = FC<ScopedSelectModelsProps> { props ->
    val targets = props.renderModel.allTargets.useCollected(emptyMap())
    val model = props.scope.get<KonstructionModel>()
    val renderedModels = model.rendered.useCollected(emptyMap())
    val keys = targets.keys.sorted()
    var targetPicker by useState<Pair<HTMLButtonElement, DisplayTarget>?>(null)
    div {
        css {
            this.paddingLeft = 16.px
            this.paddingRight = 32.px
            height = "calc(100vh - 64px)".unsafeCast<Length>()
            overflow = Auto.auto
        }
        mui.material.List {
            for (key in keys) {
                ListItem {
                    ListItemButton {
                        val state = targets[key] ?: error("Lost state")
                        ListItemText {
                            this.primary = Typography.create {
                                +prettify(key)
                            }
                        }
                        this.onClick = {
                            props.renderModel.setTargetEnabled(key, !state.isEnabled)
                        }
                        renderedModels[key]?.let { path ->
                            IconButton {
                                ariaLabel = "download"
                                Download()
                                onClick = {
                                    window.open(path, "_blank")
                                    it.stopPropagation()
                                }
                            }
                        }
                        IconButton {
                            ariaLabel = "color"
                            Colorize {
                                sx {
                                    color = Color(state.color)
                                }
                            }
                            onClick = {
                                println("TargetPicker: $state")
                                targetPicker = it.currentTarget to state
                                it.stopPropagation()
                            }
                        }
                        Switch {
                            edge = SwitchBaseEdge.end
                            this.color = primary
                            this.checked = state.isEnabled
                        }
                    }
                }
            }
        }
        Popover {
            id = targetPicker?.second?.name
            open = targetPicker != null
            anchorEl = targetPicker?.first
            anchorOrigin = buildExt<PopoverOrigin> {
                vertical = "bottom"
                horizontal = "right"
            }
            onClose = { _, _ ->
                targetPicker = null
            }
            ColorPicker {
                labelText = "Picking color for ${targetPicker?.second?.name}"
                selected = targetPicker?.second?.color ?: "#ffffff"
                onSelected = { color ->
                    targetPicker = targetPicker!!.first to targetPicker!!.second.copy(
                        color = color
                    )
                    props.renderModel.setTargetColor(targetPicker!!.second.name, color)
                }
            }
        }
    }
}

external interface ColorPickerProps : Props {
    var labelText: String
    var selected: String
    var onSelected: (String) -> Unit
}

val colorOptions = listOf(
    "#000000",
    "#cc0000",
    "#4e9a06",
    "#c4a000",
    "#729fcf",
    "#75507b",
    "#06989a",
    "#d3d7cf",
    "#555753",
    "#ef2929",
    "#8ae234",
    "#fce94f",
    "#32afff",
    "#ad7fa8",
    "#34e2e2",
    "#ffffff"
)

val ColorPicker = FC<ColorPickerProps> { props ->
    div {
        css {
            padding = 16.px
            paddingLeft = 32.px
        }
        Typography {
            css {
                paddingBottom = 8.px
            }
            variant = subtitle1
            +props.labelText
        }
        for (colorRow in colorOptions.chunked(8)) {
            div {
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.row
                }
                for (color in colorRow) {
                    Box {
                        css {
                            if (props.selected == color) {
                                border = Border(2.px, LineStyle.double, Color(color))
                                padding = 2.px
                            } else {
                                padding = 4.px
                            }
                        }
                        Box {
                            css {
                                width = 64.px
                                height = 64.px
                                backgroundColor = Color(color)
                                hover {
                                    opacity = number(.9)
                                }
                            }
                            onClick = {
                                props.onSelected(color)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun prettify(key: String): String = key
