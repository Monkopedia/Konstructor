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

import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.model.GlControlsModel
import com.monkopedia.konstructor.frontend.model.GlControlsModel.DirectionalLight
import com.monkopedia.konstructor.frontend.theme
import com.monkopedia.konstructor.frontend.utils.useCollected
import csstype.AlignItems
import csstype.Auto.auto
import csstype.Color
import csstype.Display
import csstype.FlexDirection
import csstype.JustifyContent
import csstype.important
import csstype.pct
import csstype.px
import emotion.react.css
import kotlin.math.roundToInt
import mui.icons.material.Add
import mui.icons.material.Delete
import mui.material.Card
import mui.material.CardContent
import mui.material.Divider
import mui.material.FormControlVariant
import mui.material.IconButton
import mui.material.PaperVariant.outlined
import mui.material.Size.large
import mui.material.TextField
import mui.material.Typography
import org.koin.core.component.get
import react.FC
import react.Props
import react.create
import react.dom.aria.ariaLabel
import react.dom.html.ReactHTML.div
import react.dom.onChange
import react.useMemo

val GlSettingsPane = FC<Props> {
    val ambient = RootScope.useCollected(0.0) {
        get<GlControlsModel>().ambientLight
    }
    val setAmbient = useMemo(RootScope) {
        RootScope.get<GlControlsModel>()::setAmbientLight
    }
    val lights = RootScope.useCollected(emptyList()) {
        get<GlControlsModel>().lights
    }
    val setLights = useMemo(RootScope) {
        RootScope.get<GlControlsModel>()::setLights
    }
    div {
        css {
            background = Color(theme.palette.background.paper)
        }
        css {
            width = 100.pct
            this.display = Display.flex
            this.flexDirection = FlexDirection.column
            paddingLeft = 16.px
            paddingTop = 32.px
            paddingRight = 40.px
            paddingBottom = 32.px
        }
        SliderRow {
            label = "Ambient Light"
            min = 0
            max = 100
            value = (ambient * 100.0).roundToInt()
            onValueChanged = {
                setAmbient(it / 100.0)
            }
        }
        Divider()
        div {
            css {
                width = 100.pct
                display = Display.flex
                flexDirection = FlexDirection.row
                alignItems = AlignItems.center
                justifyContent = JustifyContent.spaceBetween
            }
            Typography {
                +"Directional Lights"
            }
            IconButton {
                css {
                    marginLeft = auto
                }
                size = large
                ariaLabel = "add"
                Add()
                onClick = {
                    setLights(lights + DirectionalLight(0.5, 0.0, 0.0, -1.0))
                }
            }
        }
        for ((i, light) in lights.withIndex()) {
            Card {
                variant = outlined
                css {
                    marginBottom = 16.px
                }
                CardContent {
                    css {
                        paddingTop = important(0.px)
                        paddingBottom = important(16.px)
                        paddingLeft = important(0.px)
                        paddingRight = important(0.px)
                    }
                    div {
                        css {
                            paddingLeft = 16.px
                            marginBottom = 16.px
                            display = Display.flex
                            flexDirection = FlexDirection.row
                            alignItems = AlignItems.center
                            justifyContent = JustifyContent.spaceBetween
                        }
                        Typography {
                            +"Light ${i + 1}"
                        }
                        IconButton {
                            css {
                                marginLeft = auto
                            }
                            size = large
                            ariaLabel = "remove"
                            Delete()
                            onClick = {
                                setLights(
                                    lights.mapIndexedNotNull { index, light ->
                                        if (index == i) null
                                        else light
                                    }
                                )
                            }
                        }
                    }

                    div {
                        css {
                            marginLeft = 16.px
                            marginRight = 16.px
                            paddingRight = 16.px
                        }
                        SliderRow {
                            label = "Intensity"
                            min = 0
                            max = 100
                            value = (light.intensity * 100.0).roundToInt()
                            onValueChanged = {
                                setLights(
                                    lights.mapIndexed { index, light ->
                                        if (index == i) light.copy(intensity = it / 100.0)
                                        else light
                                    }
                                )
                            }
                        }
                    }
                    div {
                        css {
                            marginTop = 8.px
                            paddingLeft = 16.px
                            paddingRight = 16.px
                            width = auto
                            this.display = Display.flex
                            this.flexDirection = FlexDirection.row
                            this.alignItems = AlignItems.center
                            justifyContent = JustifyContent.spaceBetween
                        }
                        TextField {
                            css {
                                marginRight = 16.px
                            }
                            label = Typography.create {
                                +"X"
                            }
                            defaultValue = light.x.toString()
                            variant = FormControlVariant.outlined
                            onChange = { e ->
                                val newValue =
                                    e.target.asDynamic().value.toString().toDoubleOrNull()
                                        ?: 0.0
                                setLights(
                                    lights.mapIndexed { index, light ->
                                        if (index == i) light.copy(x = newValue)
                                        else light
                                    }
                                )
                            }
                        }
                        TextField {
                            css {
                                marginRight = 16.px
                            }
                            label = Typography.create {
                                +"Y"
                            }
                            defaultValue = light.y.toString()
                            variant = FormControlVariant.outlined
                            onChange = { e ->
                                val newValue =
                                    e.target.asDynamic().value.toString().toDoubleOrNull()
                                        ?: 0.0
                                setLights(
                                    lights.mapIndexed { index, light ->
                                        if (index == i) light.copy(y = newValue)
                                        else light
                                    }
                                )
                            }
                        }
                        TextField {
                            label = Typography.create {
                                +"Z"
                            }
                            defaultValue = light.z.toString()
                            variant = FormControlVariant.outlined
                            onChange = { e ->
                                val newValue =
                                    e.target.asDynamic().value.toString().toDoubleOrNull()
                                        ?: 0.0
                                setLights(
                                    lights.mapIndexed { index, light ->
                                        if (index == i) light.copy(z = newValue)
                                        else light
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
