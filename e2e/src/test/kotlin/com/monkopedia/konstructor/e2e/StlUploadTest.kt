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
    @org.junit.Ignore("Upload STL dialog interaction needs further investigation")
    fun testUploadStlFile() {
        loadApp()
        createFirstWorkspaceViaUi("UploadWs")
        ensureNavigationWithExpandedWorkspace("UploadWs")

        // Click "Upload STL" text via JS (onClick is on ListItemText, not ListItemButton)
        page.evaluate("""
            const texts = document.querySelectorAll('.MuiListItemText-root');
            for (const text of texts) {
                if (text.textContent && text.textContent.includes('Upload STL')) {
                    text.click();
                    break;
                }
            }
        """)
        page.waitForTimeout(1000.0)

        // Wait for upload dialog to open
        page.waitForTimeout(1000.0)

        // Create temp STL file
        val stlFile = Files.createTempFile("test-", ".stl").toFile()
        stlFile.writeText(MINIMAL_STL)
        stlFile.deleteOnExit()

        // Set file via input[type=file] — use JS to find the visible dialog's file input
        page.evaluate("""(path) => {
            const dialogs = document.querySelectorAll('.MuiDialog-root');
            for (const dialog of dialogs) {
                if (getComputedStyle(dialog).display !== 'none') {
                    const input = dialog.querySelector('input[type="file"]');
                    if (input) return true;
                }
            }
            return false;
        }""", stlFile.absolutePath)

        // Use Playwright's setInputFiles on the file input
        val fileInput = page.locator("input[type='file']")
        fileInput.setInputFiles(stlFile.toPath())
        page.waitForTimeout(500.0)

        // Fill the name input (the non-file input in the dialog)
        val nameInput = page.locator(
            ".MuiDialog-root:not([style*='display: none']) input:not([type='file'])"
        )
        nameInput.fill("UploadedMesh")
        page.waitForTimeout(200.0)

        // Click Upload button
        page.locator("button:has-text('Upload')").click()
        page.waitForTimeout(3000.0)

        // Verify the uploaded konstruction appears
        ensureNavigationWithExpandedWorkspace("UploadWs")
        page.waitForTimeout(1000.0)
        val content = page.content()
        assertTrue(
            content.contains("UploadedMesh"),
            "Uploaded STL should appear in navigation"
        )
    }
}
