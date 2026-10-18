package nl.lamia.kingdoms.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.time.Duration.Companion.minutes

/**
 * [runComposeUiTest] with room for slow machines. Its default limit is 60 s of real time; the play
 * tests take ~25 s on an M-series Mac and failed that limit on GitHub's Linux runner, which draws
 * in software.
 */
@OptIn(ExperimentalTestApi::class)
fun runUiTest(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest(testTimeout = 5.minutes, block = block)
