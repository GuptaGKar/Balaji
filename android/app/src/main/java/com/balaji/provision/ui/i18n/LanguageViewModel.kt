package com.balaji.provision.ui.i18n

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

/**
 * Stores the customer's language choice next to the theme, so it survives a
 * process death and is applied before the first customer screen composes.
 */
@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @Named("appLanguageKey") private val appLanguageKey: Preferences.Key<String>
) : ViewModel() {

    val language: StateFlow<AppLanguage> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> AppLanguage.fromTag(preferences[appLanguageKey]) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.ENGLISH)

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            dataStore.edit { preferences -> preferences[appLanguageKey] = language.tag }
        }
    }
}
