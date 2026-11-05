package com.balaji.provision.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.repository.StorefrontRepository
import com.balaji.provision.data.repository.StorefrontSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Composed content for the Categories page.
 *
 * Kept separate from HomeViewModel because the two pages compose independently: a manager can
 * put sections on Categories and none on Home, and each must fall back on its own. The
 * repository is shared, so the cache and the five-minute window are the same rule everywhere.
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val storefrontRepository: StorefrontRepository
) : ViewModel() {

    private val _sections = MutableStateFlow<List<StorefrontSection>>(emptyList())
    val sections: StateFlow<List<StorefrontSection>> = _sections.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _sections.value = storefrontRepository.sectionsFor(page = "categories")
        }
    }

    /** Forces a read; the repository still decides whether the cached copy is fresh. */
    fun refresh() {
        load()
    }
}
