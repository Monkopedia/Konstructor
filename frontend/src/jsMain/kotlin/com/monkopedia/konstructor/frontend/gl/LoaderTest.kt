package com.monkopedia.konstructor.frontend.gl

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.model.GlControlsModel
import com.monkopedia.konstructor.frontend.utils.useEffect
import info.laht.threekt.cameras.PerspectiveCamera
import info.laht.threekt.external.controls.OrbitControls
import info.laht.threekt.external.libs.Stats
import info.laht.threekt.external.loaders.STLLoader
import info.laht.threekt.lights.AmbientLight
import info.laht.threekt.lights.DirectionalLight
import info.laht.threekt.lights.Light
import info.laht.threekt.materials.MeshPhongMaterial
import info.laht.threekt.math.ColorConstants
import info.laht.threekt.objects.Mesh
import info.laht.threekt.renderers.WebGLRenderer
import info.laht.threekt.renderers.WebGLRendererParams
import info.laht.threekt.scenes.Scene
import kotlinx.browser.window
import kotlinx.coroutines.flow.combine
import org.koin.core.component.get
import org.w3c.dom.Element
import react.FC
import react.Props
import react.RefCallback
import react.dom.html.ReactHTML.div
import react.router.LocationContext
import react.useState

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
        RefCallback { v: Element? ->
            GLWindow.setElement(v)
        }
    }

    div {
        ref = callbackRef
    }
    LocationContext
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

object GLWindow {

    private var lastElement: Element? = null
    private val stats: Stats = Stats()
    private val renderer: WebGLRenderer
    private val camera: PerspectiveCamera
    private val controls: OrbitControls
    val scene: Scene = Scene()
    val models: MutableList<Mesh> = ArrayList()
    val currentLights = mutableListOf<Light>()

    init {
        val light = DirectionalLight(color = 0xffffff, intensity = 0.5)
        light.position.set(1, 1, -1)
        scene.add(light)
        currentLights.add(light)

        camera =
            PerspectiveCamera(75, window.innerWidth.toDouble() / 2 / window.innerHeight, 0.1, 1000)
        camera.position.set(0, 5, -5)

        renderer = WebGLRenderer(
            WebGLRendererParams(
                antialias = true
            )
        ).apply {
            setClearColor(ColorConstants.skyblue, alpha = 1)
            setSize(window.innerWidth / 2, window.innerHeight)
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

    fun setElement(element: Element?) {
        lastElement?.removeChild(renderer.domElement)
        lastElement?.removeChild(stats.dom)
        element?.appendChild(renderer.domElement)
        element?.appendChild(stats.dom)
        stats.dom.asDynamic().style.left = null
        lastElement = element
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
        renderer.render(scene, camera)
        stats.update()
    }
}
