package com.monkopedia.konstructor.frontend

import com.ccfraser.muirwik.components.mThemeProvider
import info.laht.threekt.cameras.PerspectiveCamera
import info.laht.threekt.core.Clock
import info.laht.threekt.external.controls.OrbitControls
import info.laht.threekt.external.libs.Stats
import info.laht.threekt.external.libs.datgui.GUIParams
import info.laht.threekt.external.libs.datgui.NumberController
import info.laht.threekt.external.libs.datgui.dat
import info.laht.threekt.external.loaders.OBJLoader
import info.laht.threekt.external.loaders.OBJLoader2
import info.laht.threekt.external.loaders.STLLoader
import info.laht.threekt.lights.DirectionalLight
import info.laht.threekt.materials.MeshPhongMaterial
import info.laht.threekt.math.ColorConstants
import info.laht.threekt.objects.Mesh
import info.laht.threekt.renderers.WebGLRenderer
import info.laht.threekt.renderers.WebGLRendererParams
import info.laht.threekt.scenes.Scene
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.css.Display
import kotlinx.css.FlexDirection
import kotlinx.css.LinearDimension
import kotlinx.css.display
import kotlinx.css.flexDirection
import kotlinx.css.width
import kotlinx.html.id
import react.dom.attrs
import react.dom.render
import styled.css
import styled.styledDiv
import kotlin.math.pow

val x = 2.0.pow(3)

fun main() {
//    val y = rand()
    kotlinext.js.require("codemirror/lib/codemirror.css")
    kotlinext.js.require("codemirror/mode/gfm/gfm.js")
    kotlinext.js.require("codemirror/keymap/vim.js")
    kotlinext.js.require("codemirror/addon/dialog/dialog.js")
    kotlinext.js.require("codemirror/addon/dialog/dialog.css")
    render(document.getElementById("root")) {
        mThemeProvider(theme = theme) {
            styledDiv {
                css {
                    this.display = Display.flex
                    this.flexDirection = FlexDirection.row
                }
                styledDiv {
                    css {
                        width = LinearDimension("50%")
                    }
                    attrs { this.id = "container" }
                }
                styledDiv {
                    css {
                        width = LinearDimension("50%")
                    }
                    child(CodeMirrorScreen::class) {
                    }
                }
            }
        }
    }
    LoaderTest().animate()
}

class LoaderTest {

    val stats: Stats = Stats()
    val renderer: WebGLRenderer
    val scene: Scene = Scene()
    val camera: PerspectiveCamera
    val controls: OrbitControls
    val models: MutableList<Mesh> = ArrayList()
    @JsName("speed")
    var speed: Double = 1.0
    val clock: Clock = Clock(autoStart = true)

    init {

        val light = DirectionalLight(color = 0xffffff, intensity = 0.5)
        light.position.set(0, 0, -1)
        scene.add(light)

        camera = PerspectiveCamera(75, window.innerWidth.toDouble() / window.innerHeight, 0.1, 1000)
        camera.position.set(0, 5, -5)

        renderer = WebGLRenderer(
            WebGLRendererParams(
                antialias = true
            )
        ).apply {
            setClearColor(ColorConstants.skyblue, alpha = 1)
            setSize(window.innerWidth, window.innerHeight)
        }

        dat.GUI(
            GUIParams(
                closed = false
            )
        ).also {
            @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
            (it.add(this, "speed") as NumberController).apply {
                min(0).max(10).step(0.1)
            }
        }

        document.getElementById("container")?.apply {
            appendChild(renderer.domElement)
            appendChild(stats.dom)
        }

        controls = OrbitControls(camera, renderer.domElement)

        STLLoader().apply {
            load("models/suzanne.stl", {
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

        OBJLoader().apply {
            load("models/suzanne.obj", {

                it.position.setX(-5)
                models.add(it)
                scene.add(it)
            })
        }

        OBJLoader2().apply {
            load("models/suzanne.obj", {

                it.detail.loaderRootNode.let {

                    it.position.setX(5)
                    it.traverse {
                        if (it is Mesh) {
                            it.material.asDynamic().color.set(0x00ff00)
                        }
                    }

                    models.add(it)
                    scene.add(it)
                }
            })
        }

        window.addEventListener("resize", {
            camera.aspect = window.innerWidth.toDouble() / window.innerHeight
            camera.updateProjectionMatrix()
            renderer.setSize(window.innerWidth, window.innerHeight)
        }, false)
    }

    fun animate() {
        window.requestAnimationFrame {

            val dt = clock.getDelta()
            models.forEach {
                it.rotation.apply {
                    y += speed * dt
                }
            }
            animate()
        }
        renderer.render(scene, camera)
        stats.update()
    }
}
