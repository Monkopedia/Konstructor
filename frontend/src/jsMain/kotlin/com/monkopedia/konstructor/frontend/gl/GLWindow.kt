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
package com.monkopedia.konstructor.frontend.gl

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.model.GlControlsModel
import com.monkopedia.konstructor.frontend.utils.useCollected
import com.monkopedia.konstructor.frontend.utils.useEffect
import csstype.Properties
import emotion.react.css
import info.laht.threekt.cameras.PerspectiveCamera
import info.laht.threekt.external.controls.OrbitControls
import info.laht.threekt.external.libs.Stats
import info.laht.threekt.external.loaders.STLLoader
import info.laht.threekt.lights.AmbientLight
import info.laht.threekt.lights.DirectionalLight
import info.laht.threekt.lights.Light
import info.laht.threekt.materials.MeshPhongMaterial
import info.laht.threekt.math.ColorConstants
import info.laht.threekt.math.Vector3
import info.laht.threekt.objects.Mesh
import info.laht.threekt.renderers.WebGLRenderer
import info.laht.threekt.renderers.WebGLRendererParams
import info.laht.threekt.scenes.Scene
import kotlinx.browser.window
import kotlinx.coroutines.flow.combine
import org.koin.core.component.get
import react.FC
import react.Props
import react.RefCallback
import react.dom.html.ReactHTML.div
import react.useRef
import react.useState
import web.cssom.Position
import web.cssom.pct
import web.cssom.px
import web.dom.Element
import web.dom.Node
import web.html.HTMLDivElement
import web.html.HTMLElement
import kotlin.math.min

external interface GLProps : Props {
    var konstruction: Konstruction?
    var konstructionPath: Map<String, Pair<String, String>>?
    var reload: Int
}

data class GLState(
    var currentKonstruction: Konstruction? = null
)

val GLComponent = FC<GLProps> { props ->
    val callbackRef by useState {
        RefCallback { v: HTMLDivElement? ->
            GLWindow.setElement(v)
        }
    }

    div {
        div {
            css {
                width = 100.pct
                height = 100.pct
            }
            ref = callbackRef
        }
        MaybeStatsComponent()
        MaybeCameraWidget()
    }
    useEffect(props.konstructionPath ?: emptyMap<String, Pair<String, String>>(), props.reload) {
        val path = props.konstructionPath
        println("Loading path $path")
        if (path != null) {
            GLWindow.loadModels(path)
        }
    }
    useEffect {
        val model = RootScope.get<GlControlsModel>()
        combine(model.ambientLight, model.lights, ::Pair)
            .collect { (ambient, lights) ->
                var lights = lights.map {
                    DirectionalLight(0xffffff, it.intensity).apply {
                        position.set(it.x, it.y, it.z)
                    } as Light
                }
                if (ambient != 0.0) {
                    lights = lights + AmbientLight(0xffffff, ambient)
                }
                GLWindow.setLighting(lights)
            }
    }
}

val MaybeStatsComponent = FC<Props> {
    val showFps = RootScope.settingsModel.showFps.useCollected(false)
    if (showFps) {
        StatsComponent()
    }
}

val StatsComponent = FC<Props> {
    val parentRef = useRef<HTMLDivElement>()
    div {
        css {
            width = 100.pct
            height = 100.pct
        }
        ref = parentRef
    }
    react.useEffect(parentRef) {
        val parent = parentRef.current ?: return@useEffect
        parent.appendChild(GLWindow.statsElement)
        GLWindow.statsElement.asDynamic().style.left = null
        cleanup {
            parent.removeChild(GLWindow.statsElement)
        }
    }
}

val MaybeCameraWidget = FC<Props> {
    val showCameraWidget = RootScope.settingsModel.showCameraWidget.useCollected(false)
    if (showCameraWidget) {
        CameraWidget()
    }
}

val CameraWidget = FC<Props> {
    val parentRef = useRef<HTMLDivElement>()
    div {
        css {
            width = 100.pct
            height = 100.pct
        }
        ref = parentRef
    }
    react.useEffect(parentRef) {
        val parent = parentRef.current ?: return@useEffect
        GLWindow.setOrientationElement(parent)
        cleanup {
            GLWindow.setOrientationElement(null)
        }
    }
}

object GLWindow {

    private var lastElement: Element? = null
    private var lastOrientationElement: Element? = null
    private val stats: Stats = Stats()
    private val renderer: WebGLRenderer
    private val orientationRenderer: WebGLRenderer
    private val camera: PerspectiveCamera
    private val orientationCamera: PerspectiveCamera
    private val controls: OrbitControls

    val statsElement: Node
        get() = stats.dom
    val scene: Scene = Scene()
    val orientationScene: Scene = Scene()
    val models: MutableList<Mesh> = ArrayList()
    val currentLights = mutableListOf<Light>()

    init {
        val light = DirectionalLight(color = 0xffffff, intensity = 0.5)
        light.position.set(1, 1, -1)
        scene.add(light)
        currentLights.add(light)

        orientationScene.add(AmbientLight())
        STLLoader().load("models/arrow.stl", {
            val matZ = MeshPhongMaterial().apply {
                color.set(0xff0000)
                specular.set(0x111111)
                shininess = 200.0
            }
            val z = Mesh(it, matZ)
            z.lookAt(0.0, 0.0, 1.0)
            orientationScene.add(z)
            val matX = MeshPhongMaterial().apply {
                color.set(0x00ff00)
                specular.set(0x111111)
                shininess = 200.0
            }
            val x = Mesh(it, matX)
            x.lookAt(1.0, 0.0, 0.0)
            orientationScene.add(x)
            val matY = MeshPhongMaterial().apply {
                color.set(0x0000ff)
                specular.set(0x111111)
                shininess = 200.0
            }
            val y = Mesh(it, matY)
            y.lookAt(0.0, 1.0, 0.0)
            orientationScene.add(y)
        })

        camera =
            PerspectiveCamera(75, window.innerWidth.toDouble() / 2 / window.innerHeight, 0.1, 1000)
        camera.up.set(0.0, 0.0, 1.0)
        camera.position.set(0, -5, 5)
        camera.lookAt(0.0, 0.0, 0.0)

        orientationCamera = PerspectiveCamera(75, 1.0, 0.1, 1000)
        orientationCamera.up.set(0.0, 0.0, 1.0)
        orientationCamera.position.set(0, -5, 5)
        orientationCamera.lookAt(0.0, 0.0, 0.0)

        renderer = WebGLRenderer(WebGLRendererParams(antialias = true)).apply {
            setClearColor(ColorConstants.skyblue, alpha = 1)
            setSize(window.innerWidth / 2, window.innerHeight)
        }
        orientationRenderer = WebGLRenderer(WebGLRendererParams(antialias = true)).apply {
            setClearColor(ColorConstants.darkgray, alpha = 1)
            val min = min(window.innerWidth / 20, window.innerHeight / 10)
            setSize(min, min)
        }

        controls = OrbitControls(camera, renderer.domElement)

        window.addEventListener("resize", {
            camera.aspect = window.innerWidth.toDouble() / 2 / window.innerHeight
            camera.updateProjectionMatrix()
            renderer.setSize(window.innerWidth / 2, window.innerHeight)
        }, false)
        animate()
    }

    fun loadModels(locations: Map<String, Pair<String, String>>) {
        for (model in models) {
            scene.remove(model)
        }
        models.clear()

        for ((location, colorStr) in locations.values) {
            STLLoader().load(location, {
                val material = MeshPhongMaterial().apply {
                    color.set(colorStr.trimStart('#').toInt(16))
                    specular.set(0x111111)
                    shininess = 200.0
                }
                val model = Mesh(it, material)
                models.add(model)
                scene.add(model)
            })
        }
    }

    fun setElement(element: HTMLElement?) {
        lastElement?.removeChild(renderer.domElement)
        element?.appendChild(renderer.domElement)
//            element?.appendChild(stats.dom)
//            stats.dom.asDynamic().style.left = null
        lastElement = element
    }

    fun setOrientationElement(element: Element?) {
        lastOrientationElement?.removeChild(orientationRenderer.domElement)
        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        val style = orientationRenderer.domElement.asDynamic().style as Properties
        style.position = Position.fixed
        style.bottom = 0.px
        element?.appendChild(orientationRenderer.domElement)
        lastOrientationElement = element
    }

    fun setLighting(lights: List<Light>) {
        for (light in currentLights) {
            scene.remove(light)
        }
        currentLights.clear()
        currentLights.addAll(lights)
        for (light in currentLights) {
            scene.add(light)
        }
    }

    fun animate() {
        window.requestAnimationFrame {
            animate()
        }
        val desiredPosition = camera.getWorldDirection()
        orientationCamera.position.x = -desiredPosition.x * 9.0
        orientationCamera.position.y = -desiredPosition.y * 9.0
        orientationCamera.position.z = -desiredPosition.z * 9.0
        orientationCamera.lookAt(0, 0, 0)
        renderer.render(scene, camera)
        orientationRenderer.render(orientationScene, orientationCamera)
        stats.update()
    }
}

private fun Vector3.str(): String {
    return "($x $y $z)"
}
