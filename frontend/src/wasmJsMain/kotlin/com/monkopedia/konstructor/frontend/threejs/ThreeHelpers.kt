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

@JsFun("() => ({ antialias: true })")
external fun createRendererParams(): JsAny

@JsFun("() => window.innerWidth")
external fun getWindowWidth(): Int

@JsFun("() => window.innerHeight")
external fun getWindowHeight(): Int

@JsFun("() => window.devicePixelRatio || 1.0")
external fun getDevicePixelRatio(): Double

@JsFun("(fn) => window.requestAnimationFrame(fn)")
external fun requestAnimationFrame(callback: (Double) -> Unit): Int

@JsFun("(id) => window.cancelAnimationFrame(id)")
external fun cancelAnimationFrame(id: Int)

@JsFun("(id) => document.getElementById(id)")
external fun getElementById(id: String): JsAny?

@JsFun(
    """(id) => {
    let canvas = document.getElementById(id);
    if (!canvas) {
        canvas = document.createElement('canvas');
        canvas.id = id;
        canvas.style.width = '100%';
        canvas.style.height = '100%';
        canvas.style.display = 'block';
        var glPane = document.getElementById('gl-pane');
        if (glPane) {
            glPane.appendChild(canvas);
        } else {
            document.body.appendChild(canvas);
        }
    }
    return canvas;
}"""
)
external fun getOrCreateCanvas(id: String): JsAny

@JsFun(
    """(canvas, left, top, width, height) => {
    canvas.style.position = 'absolute';
    canvas.style.left = left + 'px';
    canvas.style.top = top + 'px';
    canvas.style.width = width + 'px';
    canvas.style.height = height + 'px';
}"""
)
external fun positionCanvas(canvas: JsAny, left: Int, top: Int, width: Int, height: Int)

@JsFun(
    """(canvas) => {
    if (canvas && canvas.parentNode) {
        canvas.parentNode.removeChild(canvas);
    }
}"""
)
external fun removeCanvas(canvas: JsAny)

@JsFun("(canvas) => ({ canvas: canvas, antialias: true, alpha: true })")
external fun createRendererParamsWithCanvas(canvas: JsAny): JsAny

@JsFun("(el) => el.getBoundingClientRect().width")
external fun getElementWidth(el: JsAny): Double

@JsFun("(el) => el.getBoundingClientRect().height")
external fun getElementHeight(el: JsAny): Double

@JsFun("(el) => el.getBoundingClientRect().left")
external fun getElementLeft(el: JsAny): Double

@JsFun("(el) => el.getBoundingClientRect().top")
external fun getElementTop(el: JsAny): Double

@JsFun("() => ({ color: 0xff5722, specular: 0x111111, shininess: 200 })")
external fun createPhongMaterialParams(): JsAny

@JsFun("(msg) => console.log(msg)")
external fun consoleLog(msg: String)

@JsFun("(msg) => console.error(msg)")
external fun consoleError(msg: String)

@JsFun("(id) => { var el = document.getElementById(id); return el ? el.clientWidth : 0; }")
external fun getContainerWidth(id: String): Int

@JsFun("(id) => { var el = document.getElementById(id); return el ? el.clientHeight : 0; }")
external fun getContainerHeight(id: String): Int
