package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import info.laht.threekt.cameras.PerspectiveCamera
import info.laht.threekt.external.controls.OrbitControls
import info.laht.threekt.external.libs.Stats
import info.laht.threekt.external.loaders.STLLoader
import info.laht.threekt.lights.DirectionalLight
import info.laht.threekt.materials.MeshPhongMaterial
import info.laht.threekt.math.ColorConstants
import info.laht.threekt.objects.Mesh
import info.laht.threekt.renderers.WebGLRenderer
import info.laht.threekt.renderers.WebGLRendererParams
import info.laht.threekt.scenes.Scene
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.Element
import react.Props
import react.RBuilder
import react.RComponent
import react.RefCallback
import react.State
import react.setState
import styled.styledDiv

external interface GLProps : Props {
    var konstruction: Konstruction?
    var konstructionService: KonstructionService?
}

external interface GLState : State {
    var currentKonstruction: Konstruction?
}

class GLComponent : RComponent<GLProps, GLState>() {
    private val callbackRef = RefCallback { v: Element? ->
        GLWindow.setElement(v)
    }

    override fun RBuilder.render() {
        styledDiv {
            ref = callbackRef
        }
        val konstruction = props.konstruction ?: return
        val konstructionService = props.konstructionService ?: return
        println("Render GL ${konstruction} ${state.currentKonstruction}")
        if (state.currentKonstruction != konstruction) {
            GlobalScope.launch {
                println("Fetching rendered")
                val path = konstructionService.rendered(Unit)
                println("Path: $path")
                if (path != null) {
                    GLWindow.loadModel(path)
                }
                setState {
                    currentKonstruction = konstruction
                }
            }
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

    init {

        val light = DirectionalLight(color = 0xffffff, intensity = 0.5)
        light.position.set(0, 0, -1)
        scene.add(light)

        camera = PerspectiveCamera(75, window.innerWidth.toDouble() / 2 / window.innerHeight, 0.1, 1000)
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

    fun loadModel(location: String) {
        for (model in models) {
            scene.remove(model)
        }
        models.clear()
        STLLoader().apply {
            load(location, {
                Mesh(
                    it,
                    MeshPhongMaterial().apply {
                        color.set(0xff5533)
                        specular.set(0x111111)
                        shininess = 200.0
                    }
                ).also {
                    models.add(it)
                    scene.add(it)
                }
            })
        }
    }

    fun setElement(element: Element?) {
        lastElement?.removeChild(renderer.domElement)
        lastElement?.removeChild(stats.dom)
        element?.appendChild(renderer.domElement)
        element?.appendChild(stats.dom)
        lastElement = element
    }

    fun animate() {
        window.requestAnimationFrame {
//            val dt = clock.getDelta()
//            models.forEach {
//                it.rotation.apply {
//                    y += speed * dt
//                }
//            }
            animate()
        }
        renderer.render(scene, camera)
        stats.update()
    }
}

//class LoaderTest {
//
//    val models: MutableList<Mesh> = ArrayList()
//
//    @JsName("speed")
//    var speed: Double = 1.0
//    val clock: Clock = Clock(autoStart = true)
//
//    init {
//    }
//}
