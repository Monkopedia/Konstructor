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

@JsFun("(color) => ({ color: color, specular: 0x111111, shininess: 200 })")
external fun createPhongMaterialParamsWithColor(color: Int): JsAny

@JsFun("(mesh, color) => { if (mesh.material && mesh.material.color) mesh.material.color.set(color); }")
external fun setMeshColor(mesh: Mesh, color: Int)

@JsFun("(light, intensity) => { light.intensity = intensity; }")
external fun setLightIntensity(light: Light, intensity: Double)

@JsFun("(msg) => console.log(msg)")
external fun consoleLog(msg: String)

@JsFun("(msg) => console.error(msg)")
external fun consoleError(msg: String)

@JsFun("(id) => { var el = document.getElementById(id); return el ? el.clientWidth : 0; }")
external fun getContainerWidth(id: String): Int

@JsFun("(id) => { var el = document.getElementById(id); return el ? el.clientHeight : 0; }")
external fun getContainerHeight(id: String): Int

/**
 * Observe the size of the element with [id] and call [onResize] whenever it
 * changes. Returns a disposer that stops the observer.
 */
@JsFun(
    """(id, cb) => {
    var el = document.getElementById(id);
    if (!el || typeof ResizeObserver === 'undefined') {
        return () => {};
    }
    var ro = new ResizeObserver(function(entries) {
        for (var i = 0; i < entries.length; i++) {
            var r = entries[i].contentRect;
            cb(Math.round(r.width), Math.round(r.height));
        }
    });
    ro.observe(el);
    return () => { ro.disconnect(); };
}"""
)
external fun observeElementSize(id: String, onResize: (Int, Int) -> Unit): () -> Unit

// Global Ctrl+S interceptor — installed once at startup, calls globalThis.__konstructor_save
@JsFun(
    """() => {
    document.addEventListener('keydown', function(e) {
        if ((e.ctrlKey || e.metaKey) && e.key === 's') {
            e.preventDefault();
            e.stopPropagation();
            console.log('[save-interceptor] Ctrl+S caught, callback=' +
                (typeof globalThis.__konstructor_save));
            if (globalThis.__konstructor_save) globalThis.__konstructor_save();
        }
    }, true);
}"""
)
external fun installCtrlSListener()

// FPS overlay — simple DOM element in #gl-pane
@JsFun(
    """(fps) => {
    var el = document.getElementById('konstructor-fps');
    if (!el) {
        el = document.createElement('div');
        el.id = 'konstructor-fps';
        el.style.cssText = 'position:absolute;top:4px;left:4px;color:#0f0;' +
            'font:12px monospace;z-index:100;pointer-events:none;' +
            'background:rgba(0,0,0,0.5);padding:2px 6px;border-radius:3px;';
        var gl = document.getElementById('gl-pane');
        if (gl) { gl.style.position = 'relative'; gl.appendChild(el); }
    }
    el.textContent = fps + ' FPS';
}"""
)
external fun updateFpsOverlay(fps: Int)

@JsFun(
    """() => {
    var el = document.getElementById('konstructor-fps');
    if (el) el.remove();
}"""
)
external fun hideFpsOverlay()

@JsFun("(cb) => { globalThis.__konstructor_save = cb; }")
external fun setGlobalSaveCallback(callback: () -> Unit)

@JsFun("() => { globalThis.__konstructor_save = null; }")
external fun clearGlobalSaveCallback()

// Swap the flex order of gl-pane and compose-pane for "show code on left"
@JsFun(
    """(codeOnLeft) => {
    var gl = document.getElementById('gl-pane');
    var compose = document.getElementById('compose-pane');
    if (gl && compose) {
        if (codeOnLeft) {
            compose.style.order = '0';
            gl.style.order = '1';
        } else {
            gl.style.order = '0';
            compose.style.order = '1';
        }
    }
}"""
)
external fun setCodeOnLeft(codeOnLeft: Boolean)
