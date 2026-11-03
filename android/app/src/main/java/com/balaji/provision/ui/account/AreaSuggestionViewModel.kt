package com.balaji.provision.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balaji.provision.data.location.AreaSuggestion
import com.balaji.provision.data.location.suggestionQueryIsSearchable
import com.balaji.provision.data.repository.AreaSuggestionSource
import com.balaji.provision.data.repository.AreaSuggestions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Debounced completions for the address form, over the store's own areas and the customer's own
 * saved addresses.
 *
 * Two rules that a fast typist would otherwise notice:
 *  - the previous list is cleared the moment the word changes, so a chip can never be tapped for
 *    text that is no longer on screen;
 *  - an answer is dropped if the field moved on while it was in flight, so five keystrokes
 *    produce one visible result rather than the second one's.
 */
@HiltViewModel
class AreaSuggestionViewModel @Inject constructor(
    private val source: AreaSuggestionSource
) : ViewModel() {

    data class State(
        val query: String = "",
        val suggestions: List<AreaSuggestion> = emptyList(),
        val searching: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(raw: String) {
        // Clear first: a chip shown against a different word is worse than no chip at all.
        _state.update { it.copy(query = raw, suggestions = emptyList(), searching = false) }

        searchJob?.cancel()
        if (!suggestionQueryIsSearchable(raw)) return

        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _state.update { current -> if (current.query == raw) current.copy(searching = true) else current }

            val result = source.search(raw)

            if (_state.value.query != raw) return@launch
            val items = when (result) {
                is AreaSuggestions.Found -> result.items.take(MAX_CHIPS)
                AreaSuggestions.SearchedNothing,
                AreaSuggestions.Failed -> emptyList()
            }
            _state.update { current ->
                if (current.query == raw) current.copy(searching = false, suggestions = items) else current
            }
        }
    }

    /** Called when the editor opens a different address, so the last search cannot carry over. */
    fun reset() {
        searchJob?.cancel()
        _state.value = State()
    }

    private companion object {
        /** Long enough to skip the intermediate words a person deletes on the way. */
        const val DEBOUNCE_MS = 300L

        /** Anything past this is a list to scroll, not a choice to make. */
        const val MAX_CHIPS = 5
    }
}
