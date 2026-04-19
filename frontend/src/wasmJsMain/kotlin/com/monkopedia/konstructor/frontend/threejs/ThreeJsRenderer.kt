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

import kotlin.coroutines.resume
import kotlin.js.JsAny
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield

class ThreeJsRenderer(private val canvasId: String) {

    private val canvas: JsAny = getOrCreateCanvas(canvasId)
    private val renderer: WebGLRenderer
    private val scene: Scene = Scene()
    private val camera: PerspectiveCamera
    private val controls: OrbitControls
    private val ambientLight: AmbientLight
    private val directionalLight: DirectionalLight
    private val dynamicDirectionalLights: MutableList<DirectionalLight> = mutableListOf()

    /** Map of target name → loaded mesh. */
    private val meshes: MutableMap<String, Mesh> = mutableMapOf()

    /** Map of target name → URL currently loaded (to avoid re-loading unchanged meshes). */
    private val loadedUrls: MutableMap<String, String> = mutableMapOf()

    /** In-flight load jobs per target, so we can cancel them if the target changes. */
    private val loadJobs: MutableMap<String, Job> = mutableMapOf()

    /** Scope for async mesh loading. Cancelled in [dispose]. */
    private val loaderScope = MainScope()

    private var axesHelper: AxesHelper? = null
    private var animationFrameId: Int = 0
    private var disposed = false

    private var viewWidth = 1
    private var viewHeight = 1
    private var showFps = false
    private var lastFrameTime = 0.0
    private var frameCount = 0
    private var currentFps = 0

    init {
        renderer = WebGLRenderer(createRendererParamsWithCanvas(canvas))
        renderer.setClearColor(0x263238, 1.0)
        renderer.setPixelRatio(getDevicePixelRatio())

        camera = PerspectiveCamera(45.0, 1.0, 0.1, 1000.0)
        camera.position.set(0.0, 50.0, 100.0)

        controls = OrbitControls(camera, renderer.domElement)

        ambientLight = AmbientLight(0xffffff, 0.5)
        scene.add(ambientLight)

        // Legacy hardcoded directional light (disabled — replaced by settings-driven ones)
        directionalLight = DirectionalLight(0xffffff, 0.0)

        startAnimationLoop()
    }

    fun fillContainer(containerId: String) {
        if (disposed) return
        val w = getContainerWidth(containerId)
        val h = getContainerHeight(containerId)
        resize(w, h)
    }

    fun resize(width: Int, height: Int) {
        if (disposed) return
        if (width <= 0 || height <= 0) return
        viewWidth = width
        viewHeight = height
        renderer.setSize(width, height)
        camera.aspect = width.toDouble() / height.toDouble()
        camera.updateProjectionMatrix()
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

    /**
     * Reconcile the scene to show exactly the targets in [targets].
     * For each entry, key = target name, value = (STL url, hex color).
     * Meshes for targets not in the map are removed. New targets are loaded.
     * Existing targets with matching URL keep the same mesh but update color.
     */
    fun setTargets(targets: Map<String, Pair<String, String>>) {
        if (disposed) return

        // Remove meshes for targets no longer present
        val toRemove = meshes.keys - targets.keys
        for (name in toRemove) {
            val mesh = meshes.remove(name)
            if (mesh != null) {
                scene.remove(mesh)
            }
            loadedUrls.remove(name)
        }

        // Add/update remaining targets
        for ((name, urlAndColor) in targets) {
            val (url, color) = urlAndColor
            val existingUrl = loadedUrls[name]
            if (existingUrl == url) {
                // Same URL — just update color
                meshes[name]?.let { mesh ->
                    setMeshColor(mesh, parseHexColor(color))
                }
            } else {
                // Different (or new) URL — reload
                meshes.remove(name)?.let { scene.remove(it) }
                loadedUrls[name] = url
                loadJobs.remove(name)?.cancel()
                loadJobs[name] = loaderScope.launch {
                    loadMeshForTarget(name, url, color)
                }
            }
        }
    }

    private suspend fun loadMeshForTarget(name: String, url: String, color: String) {
        try {
            // 1. Fetch/download via STLLoader (async, non-blocking).
            val geometry = awaitStl(url)
            // Bail out if the target was removed or its URL changed during download.
            if (disposed || loadedUrls[name] != url) return

            // 2. Yield so Compose/animation can paint a frame before the heavy
            // synchronous geometry work begins (computeVertexNormals/center
            // iterate every vertex and cannot be made non-blocking without a
            // Web Worker, but yielding keeps the UI from pileup-freezing).
            yield()
            geometry.computeVertexNormals()
            if (disposed || loadedUrls[name] != url) return

            yield()
            geometry.center()
            if (disposed || loadedUrls[name] != url) return

            yield()
            val params = createPhongMaterialParamsWithColor(parseHexColor(color))
            val material = MeshPhongMaterial(params)
            val mesh = Mesh(geometry, material)
            meshes[name] = mesh
            scene.add(mesh)
            consoleLog("Loaded mesh for '$name' from $url")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            consoleError("Failed to load STL for '$name': ${e.message}")
        }
    }

    private suspend fun awaitStl(url: String): BufferGeometry =
        suspendCancellableCoroutine { cont ->
            val loader = STLLoader()
            loader.load(
                url = url,
                onLoad = { geometry ->
                    if (cont.isActive) cont.resume(geometry)
                },
                onProgress = null,
                onError = { _ ->
                    if (cont.isActive) {
                        cont.resume(BufferGeometry()) // empty fallback; caller checks disposed
                    }
                }
            )
        }

    /** Clear all meshes (e.g. on konstruction switch). */
    fun clearModel() {
        if (disposed) return
        for ((_, job) in loadJobs) {
            job.cancel()
        }
        loadJobs.clear()
        for ((_, mesh) in meshes) {
            scene.remove(mesh)
        }
        meshes.clear()
        loadedUrls.clear()
    }

    fun setShowFps(show: Boolean) {
        showFps = show
        if (!show) {
            hideFpsOverlay()
        }
    }

    fun setAmbientIntensity(value: Float) {
        setLightIntensity(ambientLight, value.toDouble())
    }

    /** Apply a list of directional lights, replacing any previously-set lights. */
    fun setDirectionalLights(configs: List<DirectionalLightInput>) {
        if (disposed) return
        for (light in dynamicDirectionalLights) {
            scene.remove(light)
        }
        dynamicDirectionalLights.clear()
        for (cfg in configs) {
            val light = DirectionalLight(0xffffff, cfg.intensity)
            light.position.set(cfg.x, cfg.y, cfg.z)
            scene.add(light)
            dynamicDirectionalLights.add(light)
        }
    }

    data class DirectionalLightInput(
        val intensity: Double,
        val x: Double,
        val y: Double,
        val z: Double
    )

    fun setShowAxesHelper(show: Boolean) {
        if (show && axesHelper == null) {
            val helper = AxesHelper(50.0)
            axesHelper = helper
            scene.add(helper)
        } else if (!show && axesHelper != null) {
            scene.remove(axesHelper!!)
            axesHelper = null
        }
    }

    private fun startAnimationLoop() {
        fun animate(time: Double) {
            if (disposed) return
            animationFrameId = requestAnimationFrame(::animate)
            controls.update()
            renderer.render(scene, camera)

            if (showFps) {
                frameCount++
                val elapsed = time - lastFrameTime
                if (elapsed >= 1000.0) {
                    currentFps = (frameCount * 1000.0 / elapsed).toInt()
                    frameCount = 0
                    lastFrameTime = time
                    updateFpsOverlay(currentFps)
                }
            }
        }
        animationFrameId = requestAnimationFrame(::animate)
    }

    fun dispose() {
        disposed = true
        loaderScope.cancel()
        loadJobs.clear()
        if (animationFrameId != 0) {
            cancelAnimationFrame(animationFrameId)
        }
        clearModel()
        setShowAxesHelper(false)
        hideFpsOverlay()
        controls.dispose()
        renderer.dispose()
        removeCanvas(canvas)
    }
}

/** Parse a hex color like "#ff5722" into an Int (0xff5722). */
private fun parseHexColor(hex: String): Int {
    val clean = hex.removePrefix("#")
    return clean.toInt(16)
}
