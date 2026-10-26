package com.balaji.provision.ui.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val key = booleanPreferencesKey("theme_test")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `light mode is the default`() = runTest(dispatcher) {
        val viewModel = ThemeViewModel(FakePreferencesDataStore(), key)

        assertFalse(viewModel.darkTheme.value)
    }

    @Test
    fun `enabling dark mode updates and persists the preference`() = runTest(dispatcher) {
        val dataStore = FakePreferencesDataStore()
        val viewModel = ThemeViewModel(dataStore, key)

        viewModel.setDarkTheme(true)
        advanceUntilIdle()

        assertTrue(viewModel.darkTheme.value)
        assertTrue(dataStore.current[key] == true)
    }

    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        val current: Preferences get() = state.value
        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
