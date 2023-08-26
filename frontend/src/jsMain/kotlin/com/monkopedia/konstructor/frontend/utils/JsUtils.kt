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
package com.monkopedia.konstructor.frontend.utils

import kotlin.coroutines.resume
import kotlinext.js.js
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.ArrayBuffer
import org.w3c.files.Blob
import org.w3c.files.FileReader

inline fun <T> buildExt(builder: T.() -> Unit): T {
    return (js { } as T).also(builder)
}

suspend fun Blob.asArrayBuffer(): ArrayBuffer {
    val fileReader = FileReader()
    return suspendCancellableCoroutine { continuation ->
        fileReader.onloadend = {
            if (continuation.isActive) {
                continuation.resume(fileReader.result)
            }
        }
        fileReader.readAsArrayBuffer(this)
    }
}
