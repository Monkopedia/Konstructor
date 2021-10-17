package com.monkopedia.konstructor.frontend

import com.ccfraser.muirwik.components.mThemeProvider
import info.laht.threekt.cameras.PerspectiveCamera
import info.laht.threekt.core.BufferGeometry
import info.laht.threekt.external.controls.OrbitControls
import info.laht.threekt.external.libs.Stats
import info.laht.threekt.extras.curves.CatmullRomCurve3
import info.laht.threekt.geometries.BoxBufferGeometry
import info.laht.threekt.lights.AmbientLight
import info.laht.threekt.materials.LineBasicMaterial
import info.laht.threekt.materials.MeshBasicMaterial
import info.laht.threekt.materials.MeshPhongMaterial
import info.laht.threekt.math.ColorConstants
import info.laht.threekt.math.Vector3
import info.laht.threekt.objects.Line
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

fun main() {
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
    WorldHello().animate()
}

class WorldHello {

    private val renderer: WebGLRenderer
    private val scene: Scene = Scene()
    private val camera: PerspectiveCamera
    private val controls: OrbitControls
    private val cube: Mesh
    private val stats: Stats = Stats()

    init {

        scene.add(AmbientLight())

        camera = PerspectiveCamera(75, window.innerWidth.toDouble() / 2f / window.innerHeight, 0.1, 1000)
        camera.position.setZ(5)

        renderer = WebGLRenderer(
            WebGLRendererParams(
                antialias = true
            )
        ).apply {
            setClearColor(ColorConstants.skyblue, 1)
            setSize(window.innerWidth / 2, window.innerHeight)
        }

        document.getElementById("container")?.apply {
            appendChild(renderer.domElement)
            appendChild(stats.dom)
        }

        controls = OrbitControls(camera, renderer.domElement)

        cube = Mesh(
            BoxBufferGeometry(1, 1, 1),
            MeshPhongMaterial().apply {
                this.color.set(ColorConstants.darkgreen)
            }
        ).also(scene::add)

        Mesh(
            cube.geometry as BufferGeometry,
            MeshBasicMaterial().apply {
                this.wireframe = true
                this.color.set(ColorConstants.black)
            }
        ).also(cube::add)

        val points = CatmullRomCurve3(
            arrayOf(
                Vector3(-10, 0, 10),
                Vector3(-5, 5, 5),
                Vector3(0, 0, 0),
                Vector3(5, -5, 5),
                Vector3(10, 0, 10)
            )
        ).getPoints(50)

        val geometry = BufferGeometry().setFromPoints(points)

        val material = LineBasicMaterial().apply {
            color.set(0xff0000)
        }

        // Create the final object to add to the scene
        Line(geometry, material).apply(scene::add)

        window.addEventListener("resize", {
            camera.aspect = window.innerWidth.toDouble() / 2f / window.innerHeight
            camera.updateProjectionMatrix()

            renderer.setSize(window.innerWidth / 2, window.innerHeight)
        }, false)
    }

    fun animate() {
        window.requestAnimationFrame {
            cube.rotation.x += 0.01
            cube.rotation.y += 0.01
            animate()
        }
        renderer.render(scene, camera)
        stats.update()
    }
}
