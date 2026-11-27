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
package com.monkopedia.konstructor.frontend.ui.editor

import com.monkopedia.konstructor.common.LspEngineState.AVAILABLE
import com.monkopedia.konstructor.common.LspEngineState.FAILURE_COOLDOWN
import com.monkopedia.konstructor.common.LspEngineState.SPAWN_WINDOW
import com.monkopedia.konstructor.common.LspStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LspStatusTextTest {
    @Test
    fun availableOrUnknownShowsNothing() {
        assertNull(lspUnavailableText(LspStatus(AVAILABLE)))
        // An older backend without /lsp_status, or a failed poll: say nothing rather than guess.
        assertNull(lspUnavailableText(null))
    }

    @Test
    fun bothBoundsShowTheRetryTimeInWholeMinutesRoundedUp() {
        assertEquals(
            "LSP unavailable (retrying in 5 min)",
            lspUnavailableText(LspStatus(FAILURE_COOLDOWN, 5 * 60_000L))
        )
        assertEquals(
            "LSP unavailable (retrying in 10 min)",
            lspUnavailableText(LspStatus(SPAWN_WINDOW, 9 * 60_000L + 1))
        )
        assertEquals(
            "LSP unavailable (retrying in 1 min)",
            lspUnavailableText(LspStatus(SPAWN_WINDOW, 1))
        )
    }
}
