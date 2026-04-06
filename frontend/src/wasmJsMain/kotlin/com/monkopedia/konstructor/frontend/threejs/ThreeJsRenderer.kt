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
package com.monkopedia.konstructor.frontend.threejs

import kotlin.js.JsAny

class ThreeJsRenderer(private val canvasId: String) {

    private val canvas: JsAny = getOrCreateCanvas(canvasId)
    private val renderer: WebGLRenderer
    private val scene: Scene = Scene()
    private val camera: PerspectiveCamera
    private val controls: OrbitControls
    private val ambientLight: AmbientLight
    private val directionalLight: DirectionalLight

    private var currentMesh: Mesh? = null
    private var animationFrameId: Int = 0
    private var disposed = false

    private var viewWidth = 1
    private var viewHeight = 1

    init {
        renderer = WebGLRenderer(createRendererParamsWithCanvas(canvas))
        renderer.setClearColor(0x263238, 1.0)
        renderer.setPixelRatio(getDevicePixelRatio())

        camera = PerspectiveCamera(45.0, 1.0, 0.1, 1000.0)
        camera.position.set(0.0, 50.0, 100.0)

        controls = OrbitControls(camera, renderer.domElement)
        controls.enableDamping = true
        controls.dampingFactor = 0.05

        ambientLight = AmbientLight(0x404040, 1.0)
        scene.add(ambientLight)

        directionalLight = DirectionalLight(0xffffff, 1.0)
        directionalLight.position.set(50.0, 50.0, 50.0)
        scene.add(directionalLight)

        startAnimationLoop()
    }

    fun fillContainer(containerId: String) {
        if (disposed) return
        val w = getContainerWidth(containerId)
        val h = getContainerHeight(containerId)
        if (w > 0 && h > 0) {
            viewWidth = w
            viewHeight = h
            renderer.setSize(w, h)
            camera.aspect = w.toDouble() / h.toDouble()
            camera.updateProjectionMatrix()
        }
    }

    fun updateLayout(left: Int, top: Int, width: Int, height: Int) {
        if (disposed) return
        if (width <= 0 || height <= 0) return

        viewWidth = width
        viewHeight = height

        positionCanvas(canvas, left, top, width, height)
        renderer.setSize(width, height)

        camera.aspect = width.toDouble() / height.toDouble()
        camera.updateProjectionMatrix()
    }

    fun loadStl(url: String) {
        if (disposed) return
        consoleLog("Loading STL from: $url")

        val loader = STLLoader()
        loader.load(
            url = url,
            onLoad = { geometry ->
                if (disposed) return@load
                clearModelInternal()

                geometry.computeVertexNormals()
                geometry.center()

                val material = MeshPhongMaterial(createPhongMaterialParams())
                val mesh = Mesh(geometry, material)
                currentMesh = mesh
                scene.add(mesh)

                consoleLog("STL loaded successfully")
            },
            onProgress = null,
            onError = { _ ->
                consoleError("Failed to load STL: $url")
            }
        )
    }

    fun clearModel() {
        if (disposed) return
        clearModelInternal()
    }

    private fun clearModelInternal() {
        currentMesh?.let { mesh ->
            scene.remove(mesh)
            currentMesh = null
        }
    }

    private fun startAnimationLoop() {
        fun animate(time: Double) {
            if (disposed) return
            animationFrameId = requestAnimationFrame(::animate)
            controls.update()
            renderer.render(scene, camera)
        }
        animationFrameId = requestAnimationFrame(::animate)
    }

    fun dispose() {
        disposed = true
        if (animationFrameId != 0) {
            cancelAnimationFrame(animationFrameId)
        }
        clearModelInternal()
        controls.dispose()
        renderer.dispose()
        removeCanvas(canvas)
    }
}
