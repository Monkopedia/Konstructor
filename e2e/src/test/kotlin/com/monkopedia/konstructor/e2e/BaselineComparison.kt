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

import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Minimal pixel-difference visual-regression harness against the pre-migration
 * screenshots in `e2e/baselines/`.
 *
 * Adding a new visual check is a one-line call (see
 * [MigrationVisualRegressionTest.assertBaseline] for the asserting wrapper):
 *
 * ```
 * val diff = BaselineComparison.compare(actualPng, "01-empty-state")
 * ```
 *
 * The baseline file is `e2e/baselines/<name>.png`. Actual screenshots and any
 * generated diff images land in `build/screenshots/` for inspection.
 *
 * Tolerance is intentionally generous (perceptual, per-channel) because the
 * pre-migration baselines were captured from the React/Three.js stack and the
 * Compose/Skiko renderer is not pixel-identical. The harness is meant to catch
 * gross structural/orientation regressions (e.g. a 90° flipped model), not
 * sub-pixel antialiasing drift.
 *
 * HEADLESS LIMITATION: WebGL canvas content (the GL pane / rendered model) does
 * not read back reliably under headless Chromium even with Xvfb, so comparisons
 * that depend on canvas pixels are documented and `@Ignore`-d rather than run.
 */
object BaselineComparison {

    private val baselineDir: File by lazy {
        // e2e module dir is the test working directory.
        File(System.getProperty("user.dir"), "baselines")
    }

    private val outputDir: File by lazy {
        File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    }

    data class DiffResult(
        val baselineExists: Boolean,
        val sameSize: Boolean,
        /** Fraction of pixels exceeding [perChannelTolerance], 0.0..1.0. */
        val diffFraction: Double,
        val diffImagePath: String?
    )

    fun baselineFor(name: String): File = File(baselineDir, "$name.png")

    /**
     * Compare [actualPng] bytes against baseline `<name>.png`. Returns a
     * [DiffResult] without throwing, so callers can choose how strict to be.
     */
    fun compare(actualPng: ByteArray, name: String, perChannelTolerance: Int = 24): DiffResult {
        val actualFile = File(outputDir, "$name-actual.png")
        actualFile.writeBytes(actualPng)

        val baseline = baselineFor(name)
        if (!baseline.exists()) {
            return DiffResult(
                baselineExists = false,
                sameSize = false,
                diffFraction = 1.0,
                diffImagePath = null
            )
        }

        val baseImg = ImageIO.read(baseline) ?: return DiffResult(false, false, 1.0, null)
        val actImg = ImageIO.read(actualFile) ?: return DiffResult(true, false, 1.0, null)

        if (baseImg.width != actImg.width || baseImg.height != actImg.height) {
            return DiffResult(
                baselineExists = true,
                sameSize = false,
                diffFraction = 1.0,
                diffImagePath = actualFile.absolutePath
            )
        }

        var differing = 0L
        val total = baseImg.width.toLong() * baseImg.height.toLong()
        for (y in 0 until baseImg.height) {
            for (x in 0 until baseImg.width) {
                val a = baseImg.getRGB(x, y)
                val b = actImg.getRGB(x, y)
                val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
                val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
                val db = abs((a and 0xFF) - (b and 0xFF))
                if (dr > perChannelTolerance || dg > perChannelTolerance ||
                    db > perChannelTolerance
                ) {
                    differing++
                }
            }
        }
        return DiffResult(
            baselineExists = true,
            sameSize = true,
            diffFraction = differing.toDouble() / total.toDouble(),
            diffImagePath = actualFile.absolutePath
        )
    }
}
