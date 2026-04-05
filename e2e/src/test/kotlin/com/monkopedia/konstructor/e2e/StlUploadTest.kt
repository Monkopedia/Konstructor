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
package com.monkopedia.konstructor.e2e

import java.nio.file.Files
import org.junit.Test
import kotlin.test.assertTrue

class StlUploadTest : BaseE2eTest() {

    companion object {
        val MINIMAL_STL = """
            solid test
            facet normal 0 0 1
              outer loop
                vertex 0 0 0
                vertex 1 0 0
                vertex 0 1 0
              endloop
            endfacet
            endsolid test
        """.trimIndent()
    }

    @Test
    fun testUploadStlFile() {
        loadApp()
        createFirstWorkspaceViaUi("UploadWs")
        openNavigationPane()
        expandWorkspace("UploadWs")

        // Click "Upload STL"
        page.locator("text=Upload STL").first().click()
        page.waitForTimeout(1000.0)

        // Dialog should be open
        val dialog = page.waitForSelector(".MuiDialog-root", waitOpts(5000.0))

        // Create temp STL file
        val stlFile = Files.createTempFile("test-", ".stl").toFile()
        stlFile.writeText(MINIMAL_STL)
        stlFile.deleteOnExit()

        // Set file via input[type=file]
        val fileInput = page.querySelector(
            ".MuiDialog-root input[type='file']"
        )
        if (fileInput != null) {
            fileInput.setInputFiles(stlFile.toPath())
            page.waitForTimeout(500.0)
        }

        // Fill name
        val nameInput = page.querySelector(
            ".MuiDialog-root input:not([type='file'])"
        )
        if (nameInput != null) {
            nameInput.fill("UploadedMesh")
        }

        // Click Upload
        page.locator(".MuiDialog-root button:has-text('Upload')").click()
        page.waitForTimeout(3000.0)

        // Check it appeared
        openNavigationPane()
        expandWorkspace("UploadWs")
        page.waitForTimeout(1000.0)
        val content = page.content()
        assertTrue(
            content.contains("UploadedMesh"),
            "Uploaded STL should appear in navigation"
        )
    }
}
