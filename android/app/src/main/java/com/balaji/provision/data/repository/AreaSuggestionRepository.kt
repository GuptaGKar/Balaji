package com.balaji.provision.data.repository

import com.balaji.provision.BuildConfig
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.location.AreaSuggestion
import com.balaji.provision.data.location.GeoPoint
import com.balaji.provision.data.location.SuggestionType
import com.balaji.provision.data.location.suggestionQueryIsSearchable
import com.balaji.provision.data.network.AreaSuggestionRequest
import com.balaji.provision.data.network.AreaSuggestionResponse
import com.balaji.provision.data.network.SupabaseApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcomes of a completion search. `SearchedNothing` is a real answer, not a failure: with fewer
 * than two characters, or nothing matching, the form must clear the chips rather than keep
 * showing yesterday's list.
 */
sealed interface AreaSuggestions {
    data class Found(val items: List<AreaSuggestion>) : AreaSuggestions
    data object SearchedNothing : AreaSuggestions
    data object Failed : AreaSuggestions
}

/** Seam over the suggestion RPC, so the debounce and stale-answer rules are testable in a JVM. */
interface AreaSuggestionSource {
    suspend fun search(query: String): AreaSuggestions
}

@Singleton
class AreaSuggestionRepository @Inject constructor(
    private val api: SupabaseApi,
    private val authRepository: AuthRepository
) : AreaSuggestionSource {

    /**
     * Signed in, the request carries the customer's JWT and the server adds their own saved
     * addresses. Signed out it carries the anon key, and the same function answers with public
     * areas only - the private half is filtered on `auth.uid()` server-side, which is null here.
     * So an anonymous browse can complete a PIN, and no one else's address book is reachable
     * from this call either way.
     */
    override suspend fun search(query: String): AreaSuggestions {
        if (!suggestionQueryIsSearchable(query)) return AreaSuggestions.SearchedNothing

        val token = runCatching { authRepository.authorizedToken() }.getOrNull()
            ?: BuildConfig.SUPABASE_ANON_KEY

        return try {
            val response = api.searchAreaSuggestions(
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                auth = "Bearer $token",
                body = AreaSuggestionRequest(p_query = query.trim())
            )
            if (!response.isSuccessful) return AreaSuggestions.Failed
            val rows = response.body().orEmpty()
            AreaSuggestions.Found(rows.mapNotNull { it.toDomain() })
        } catch (_: Exception) {
            // An unreachable suggestion service is not an error the customer can act on: the
            // form still accepts whatever they type, so the chips simply do not appear.
            AreaSuggestions.Failed
        }
    }
}

/**
 * Drops anything the server did not label, because a chip with no text cannot be tapped
 * meaningfully, and keeps the type distinction the patch rules depend on.
 */
internal fun AreaSuggestionResponse.toDomain(): AreaSuggestion? {
    val rowId = id ?: return null
    val shown = label?.takeIf { it.isNotBlank() } ?: value?.takeIf { it.isNotBlank() } ?: return null
    val type = when (type) {
        "area" -> SuggestionType.AREA
        "address" -> SuggestionType.SAVED_ADDRESS
        else -> return null
    }
    val point = if (lat != null && lng != null) GeoPoint(latitude = lat, longitude = lng) else null

    return AreaSuggestion(
        type = type,
        id = rowId,
        label = shown,
        sublabel = when (type) {
            // A PIN is the useful half of an area row; the city is the useful half of an address.
            SuggestionType.AREA -> value?.takeIf { it.isNotBlank() && it != shown }?.let { "PIN $it" }
            SuggestionType.SAVED_ADDRESS -> listOfNotNull(area?.takeIf { it.isNotBlank() }, city?.takeIf { it.isNotBlank() })
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
        },
        kind = kind,
        value = value,
        point = point,
        recipientName = recipient_name,
        phone = phone,
        addressLines = address_lines,
        landmark = landmark,
        area = area,
        city = city,
        state = state,
        postalCode = postal_code
    )
}
