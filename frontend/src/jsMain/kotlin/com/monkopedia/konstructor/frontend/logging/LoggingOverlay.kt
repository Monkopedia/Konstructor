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
package com.monkopedia.konstructor.frontend.logging

import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.nonInvertedTheme
import com.monkopedia.konstructor.frontend.utils.useCollected
import csstype.Border
import csstype.BoxShadow
import csstype.Color
import csstype.LineStyle
import csstype.Position
import csstype.Width
import csstype.pct
import csstype.px
import csstype.translate
import dukat.reactlogviewer.LogViewer
import dukat.reactlogviewer.LogViewerSearch
import emotion.react.css
import mui.material.Box
import mui.material.Modal
import mui.material.Paper
import mui.material.Toolbar
import mui.system.ThemeProvider
import mui.system.sx
import react.FC
import react.Props
import react.create
import react.dom.html.ReactHTML.div

val LoggingOverlay = FC<Props> {
    val isOpen = RootScope.loggingModel.isLoggingOpen.useCollected(false)

    Modal {
        this.open = isOpen
        this.onClose = {
            RootScope.loggingModel.closeLogging()
        }
        div {
            GlobalLogsView {
            }
        }
    }
}

val GlobalLogsView = FC<Props> {
    val lines = RootScope.loggingModel.logLines.useCollected(emptyArray())
    Box {
        sx {
            position = Position.absolute
            top = 50.pct
            left = 50.pct
            transform = translate((-50).pct, (-50).pct)
            width = "calc(100% - 200px)".unsafeCast<Width>()
            backgroundColor = Color("background.paper")
            border = Border(2.px, LineStyle.solid, Color("#000"))
            boxShadow = 24.unsafeCast<BoxShadow>()
            asDynamic().p = 4
        }
        LogViewer {
            this.dataStringArray = lines
            this.hasLineNumbers = false
            this.isDark = true
            this.toolbar = div.create {
                ThemeProvider {
                    this.theme = nonInvertedTheme
                    Paper {
                        css {
                            height = 72.px
                        }
                        Toolbar {
                            LogViewerSearch {
                                this.placeholder = "Search..."
                            }
                        }
                    }
                }
            }
        }
    }
}
