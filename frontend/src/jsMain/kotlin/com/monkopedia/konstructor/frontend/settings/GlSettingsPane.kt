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
import com.monkopedia.konstructor.frontend.nonInvertedTheme
import com.monkopedia.konstructor.frontend.utils.useCollected
import emotion.react.css
import kotlin.math.roundToInt
import mui.icons.material.Add
import mui.icons.material.Delete
import mui.material.Card
import mui.material.CardContent
import mui.material.Divider
import mui.material.FormControlVariant
import mui.material.IconButton
import mui.material.Paper
import mui.material.PaperVariant.Companion.outlined
import mui.material.Size.Companion.large
import mui.material.TextField
import mui.material.Typography
import mui.system.sx
import org.koin.core.component.get
import react.FC
import react.Props
import react.create
import react.dom.aria.ariaLabel
import react.dom.html.ReactHTML.div
import react.dom.onChange
import react.useMemo
import web.cssom.AlignItems
import web.cssom.Auto
import web.cssom.Color
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.JustifyContent
import web.cssom.Length
import web.cssom.Overflow.Companion.visible
import web.cssom.important
import web.cssom.pct
import web.cssom.px

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
            background = Color(nonInvertedTheme.palette.background.paper)
        }
        css {
            width = 100.pct
            this.display = Display.flex
            this.flexDirection = FlexDirection.column
            paddingLeft = 16.px
            paddingTop = 32.px
            paddingRight = 40.px
            paddingBottom = 32.px
            height = "calc(100vh - 64px)".unsafeCast<Length>()
            overflowY = Auto.auto
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
                    marginLeft = Auto.auto
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
            Paper {
                variant = outlined
                sx {
                    marginBottom = 16.px
                    overflow = visible
                }
                div {
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
                                marginLeft = Auto.auto
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
                            width = Auto.auto
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
