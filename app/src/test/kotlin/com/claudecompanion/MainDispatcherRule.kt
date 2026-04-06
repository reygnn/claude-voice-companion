package com.claudecompanion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * TESTING CONVENTIONS
 *
 * - Single dispatcher via MainDispatcherRule – never create separate TestScope/StandardTestDispatcher.
 * - MockK for all dependencies (relaxed mocks preferred).
 * - Turbine for Flow testing.
 * - Use runTest {} from kotlinx-coroutines-test for suspending tests.
 */

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
