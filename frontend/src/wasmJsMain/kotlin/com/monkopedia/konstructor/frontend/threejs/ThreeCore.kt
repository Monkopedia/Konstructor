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
@file:JsModule("three")

package com.monkopedia.konstructor.frontend.threejs

import kotlin.js.JsAny

external class Scene : JsAny {
    fun add(obj: Object3D)
    fun remove(obj: Object3D)
}

external open class Object3D : JsAny {
    val position: Vector3
    val rotation: Euler
    fun lookAt(x: Double, y: Double, z: Double)
}

external class Vector3 : JsAny {
    constructor()
    constructor(x: Double, y: Double, z: Double)

    var x: Double
    var y: Double
    var z: Double
    fun set(x: Double, y: Double, z: Double): Vector3
    fun copy(v: Vector3): Vector3
}

external class Euler : JsAny {
    var x: Double
    var y: Double
    var z: Double
    fun set(x: Double, y: Double, z: Double): Euler
}

external class PerspectiveCamera(
    fov: Double = definedExternally,
    aspect: Double = definedExternally,
    near: Double = definedExternally,
    far: Double = definedExternally
) : Object3D {
    var aspect: Double
    fun updateProjectionMatrix()
}

external open class Light(
    color: Int = definedExternally,
    intensity: Double = definedExternally
) : Object3D

external class AmbientLight(
    color: Int = definedExternally,
    intensity: Double = definedExternally
) : Light

external class DirectionalLight(
    color: Int = definedExternally,
    intensity: Double = definedExternally
) : Light

external class Color : JsAny {
    fun set(color: Int): Color
    fun set(color: String): Color
}

external open class Material : JsAny {
    fun dispose()
}

external class MeshPhongMaterial(
    params: JsAny = definedExternally
) : Material {
    val color: Color
    val specular: Color
    var shininess: Double
}

external open class BufferGeometry : JsAny {
    fun computeVertexNormals()
    fun center(): BufferGeometry
    fun dispose()
}

external class Mesh(
    geometry: BufferGeometry = definedExternally,
    material: Material = definedExternally
) : Object3D

external class WebGLRenderer(
    params: JsAny = definedExternally
) : JsAny {
    val domElement: JsAny
    fun setSize(width: Int, height: Int)
    fun render(scene: Scene, camera: PerspectiveCamera)
    fun setClearColor(color: Int, alpha: Double)
    fun setPixelRatio(ratio: Double)
    fun dispose()
}

external class AxesHelper(
    size: Double = definedExternally
) : Object3D
