package se.birdy.app.premium

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BillingAnswerTest {
    @Test
    fun `already answered returns true at once`() = runTest { assertTrue(awaitBillingAnswer(MutableStateFlow(true), timeoutMs = 5_000)) }

    @Test
    fun `answer arriving before timeout returns true`() =
        runTest {
            val queried = MutableStateFlow(false)
            val result = async { awaitBillingAnswer(queried, timeoutMs = 5_000) }
            advanceTimeBy(1_000)
            queried.value = true
            assertTrue(result.await())
        }

    @Test
    fun `no answer before timeout returns false`() =
        runTest {
            assertFalse(awaitBillingAnswer(MutableStateFlow(false), timeoutMs = 5_000))
        }
}
