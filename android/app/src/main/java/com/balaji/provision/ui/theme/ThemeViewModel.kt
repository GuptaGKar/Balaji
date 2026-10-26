package com.balaji.provision.ui.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @Named("darkThemeKey") private val darkThemeKey: Preferences.Key<Boolean>
) : ViewModel() {

    val darkTheme: StateFlow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[darkThemeKey] ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[darkThemeKey] = enabled }
        }
    }
}
