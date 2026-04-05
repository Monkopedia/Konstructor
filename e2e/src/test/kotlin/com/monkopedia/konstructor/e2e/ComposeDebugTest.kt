package com.monkopedia.konstructor.e2e

import com.microsoft.playwright.Page
import java.io.File
import java.nio.file.Paths
import org.junit.Test

class ComposeDebugTest : BaseE2eTest() {
    @Test
    fun captureComposeState() {
        loadApp()
        page.waitForTimeout(10000.0)
        val dir = File(System.getProperty("user.dir"), "build/screenshots")
        dir.mkdirs()
        page.screenshot(
            Page.ScreenshotOptions()
                .setPath(Paths.get(dir.absolutePath, "compose-empty-state.png"))
                .setFullPage(true)
        )
        System.err.println("Screenshot saved")
    }
}
