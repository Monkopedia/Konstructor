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
package com.monkopedia.konstructor.frontend.ui.dialogs

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Opens a native browser file picker (`<input type=file>`) constrained to the given [accept]
 * filter. When the user selects a file, [onPicked] is invoked with the file name and its raw
 * bytes. If the user cancels (no file selected) the callback is never invoked.
 *
 * Implemented via JS interop because Compose/WasmJS has no native file dialog: the JS side reads
 * the chosen file as a base64 data URL and hands the encoded payload back to Kotlin, which decodes
 * it into a [ByteArray].
 */
@OptIn(ExperimentalEncodingApi::class)
fun pickFile(accept: String, onPicked: (name: String, bytes: ByteArray) -> Unit) {
    openFilePickerJs(accept) { name, base64 ->
        val bytes = Base64.decode(base64)
        onPicked(name, bytes)
    }
}

@JsFun(
    "(accept, onPicked) => {" +
        "const input = document.createElement('input');" +
        "input.type = 'file';" +
        "input.accept = accept;" +
        "input.style.display = 'none';" +
        "document.body.appendChild(input);" +
        "input.addEventListener('change', () => {" +
        "  const file = input.files && input.files[0];" +
        "  if (!file) { document.body.removeChild(input); return; }" +
        "  const reader = new FileReader();" +
        "  reader.onload = () => {" +
        "    const result = reader.result;" +
        "    const base64 = result.substring(result.indexOf(',') + 1);" +
        "    document.body.removeChild(input);" +
        "    onPicked(file.name, base64);" +
        "  };" +
        "  reader.readAsDataURL(file);" +
        "});" +
        "input.click();" +
        "}"
)
private external fun openFilePickerJs(
    accept: String,
    onPicked: (name: String, base64: String) -> Unit
)
