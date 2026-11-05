package com.balaji.provision.data.repository

import com.balaji.provision.BuildConfig
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.network.StorefrontRequest
import com.balaji.provision.data.network.StorefrontSectionResponse
import com.balaji.provision.data.network.SupabaseApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A composed section, resolved from the server's ids-only payload. */
data class StorefrontSection(
    val id: String,
    val title: String,
    val subtitle: String?,
    val type: SectionKind,
    val itemIds: List<String>
)

enum class SectionKind { Products, Categories }

/**
 * Manager-composed page content.
 *
 * The cache exists so a returning customer sees their last good layout instantly instead of a
 * spinner, and so a network failure mid-browse does not blank the page. It is deliberately
 * in-memory only: a section that survives an app restart could show an item the store has since
 * removed, and the ids are resolved against a catalogue that is itself re-fetched.
 */
@Singleton
class StorefrontRepository @Inject constructor(
    private val api: SupabaseApi,
    private val authRepository: AuthRepository
) {

    private class Cache(
        val page: String,
        val fetchedAtMillis: Long,
        val sections: List<StorefrontSection>
    )

    /**
     * Keyed by page. A single slot made Home and Categories evict each other on every tab
     * switch, so the cache never served either page twice and the five-minute window was
     * meaningless - the only time it helped was re-entering the same page back to back.
     * Replaced wholesale rather than mutated, because the two pages can be requested at once.
     */
    @Volatile
    private var cache: Map<String, Cache> = emptyMap()

    /**
     * Home's first paint and its re-entry refresh can easily overlap - init() reads the page at
     * the same moment the LaunchedEffect that re-enters it does. Without a single flight the
     * customer pays for two identical requests, so the second one waits and reads the cache the
     * first wrote.
     */
    private val fetchMutex = Mutex()

    /**
     * Sections for [page], or an empty list meaning "use the built-in layout".
     *
     * Empty is a real answer, not a failure: with no active sections the server returns `[]`, and
     * the apps must then look exactly as they did before this feature existed. A network error or
     * an un-migrated database also yields empty, so the shelf is never blank because of us.
     */
    suspend fun sectionsFor(page: String, force: Boolean = false): List<StorefrontSection> =
        fetchMutex.withLock {
            // Re-checked inside the lock: a concurrent call for the same page may have finished
            // and populated the cache while this one was waiting on it.
            val current = cache[page]
            if (current != null && isFresh(current.page, page, current.fetchedAtMillis, clock(), force)) {
                return@withLock current.sections
            }

            // Signed in, the request carries the customer's JWT; signed out it carries the anon key.
            // Either way RLS answers with active sections only, so this cannot expose a draft.
            val token = runCatching { authRepository.authorizedToken() }.getOrNull()
                ?: BuildConfig.SUPABASE_ANON_KEY

            val response = try {
                api.getStorefront(
                    apiKey = BuildConfig.SUPABASE_ANON_KEY,
                    auth = "Bearer $token",
                    body = StorefrontRequest(p_page = page)
                )
            } catch (e: Exception) {
                // No network, no DNS, no parse: keep whatever we last showed rather than clearing it.
                return@withLock current?.sections.orEmpty()
            }

            if (!response.isSuccessful) {
                // 400 here normally means migration 20261105000001 has not been applied to this
                // database. That is the expected state for an un-migrated store, not an error to
                // show the customer, so it resolves to the built-in layout.
                return@withLock current?.sections.orEmpty()
            }

            val body = response.body().orEmpty()
            val sections = body.mapNotNull { it.toDomain() }
            cache = cache + (page to Cache(page, clock(), sections))
            sections
        }

    /** Drops the cache so the next read goes to the server, used when the app is re-entered. */
    fun invalidate() {
        cache = emptyMap()
    }

    /** Overridable so the TTL rule is testable without waiting five minutes. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    internal companion object {
        /** Re-fetch when the customer re-enters the page after roughly this long. */
        const val REFRESH_MILLIS = 5 * 60 * 1000L

        /**
         * Whether the cached page can be served as-is. Pulled out as a pure function so the
         * three rules - same page, inside the window, not forced - are testable without a fake
         * clock, a network or a five minute wait.
         */
        fun isFresh(
            cachedPage: String?,
            requestedPage: String,
            fetchedAtMillis: Long,
            nowMillis: Long,
            force: Boolean
        ): Boolean =
            !force && cachedPage == requestedPage && nowMillis - fetchedAtMillis < REFRESH_MILLIS
    }
}

/**
 * Decodes one server row.
 *
 * An unknown `section_type` is dropped rather than guessed at, so a section type added by a
 * future server build cannot render as a random layout on an older app. That is the whole
 * point of skipping: an unrecognised shelf disappears, and everything around it still works.
 */
internal fun StorefrontSectionResponse.toDomain(): StorefrontSection? {
    val sectionId = id ?: return null
    val kind = when (section_type?.lowercase()) {
        "products" -> SectionKind.Products
        "categories" -> SectionKind.Categories
        else -> return null
    }
    return StorefrontSection(
        id = sectionId,
        // A section with no heading still renders, but an empty heading is not something a
        // manager can save server-side, so fall back to the raw value rather than inventing copy.
        title = title.orEmpty(),
        subtitle = subtitle?.takeIf { it.isNotBlank() },
        type = kind,
        itemIds = item_ids.orEmpty().filter { it.isNotBlank() }
    )
}
