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
package com.monkopedia.konstructor.testutil

object TestFixtures {

    val SIMPLE_CUBE_SCRIPT = """
        val cube = cube(xyz(10, 10, 10))
        export(cube)
    """.trimIndent()

    val MULTI_TARGET_SCRIPT = """
        val cube = cube(xyz(10, 10, 10))
        val sphere = sphere(5.0)
        export(cube)
        export(sphere)
    """.trimIndent()

    val SYNTAX_ERROR_SCRIPT = """
        val x = {{{not valid kotlin
    """.trimIndent()

    val EMPTY_SCRIPT = ""

    val RUNTIME_ERROR_SCRIPT = """
        val failing = cube(xyz(1, 1, 1)).also {
            error("intentional failure")
        }
        export(failing)
    """.trimIndent()
}
