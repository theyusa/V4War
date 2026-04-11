package fr.husi.ui.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialize with same args should reset dirty state`() = runTest(dispatcher.scheduler) {
        val viewModel = ConfigSettingsViewModel()
        backgroundScope.launch {
            viewModel.isDirty.collect {}
        }
        advanceUntilIdle()

        viewModel.initialize(editingId = -1L, isSubscription = false)
        advanceUntilIdle()
        assertFalse(viewModel.isDirty.value)

        viewModel.setName("dirty")
        advanceUntilIdle()
        assertTrue(viewModel.isDirty.value)

        viewModel.initialize(editingId = -1L, isSubscription = false)
        advanceUntilIdle()
        assertFalse(viewModel.isDirty.value)
        assertEquals("", viewModel.uiState.value.name)
    }
}
