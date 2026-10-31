package com.balaji.provision.push

import android.content.Context
import com.balaji.provision.BuildConfig
import com.balaji.provision.data.auth.AuthRepository
import com.balaji.provision.data.network.DeviceTokenBody
import com.balaji.provision.data.network.SupabaseApi
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads this install's FCM token to device_tokens for the signed-in user.
 *
 * Called on every new token and after a successful sign-in, because FCM usually issues
 * the token before anyone logs in. Safe to call repeatedly: the table is unique on the
 * token and the request uses Prefer: resolution=merge-duplicates.
 */
@Singleton
class PushRegistrar @Inject constructor(
    private val api: SupabaseApi,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context
) {
    suspend fun sync(): Boolean {
        val session = authRepository.session.value ?: return false

        val token = withContext(Dispatchers.IO) {
            runCatching { Tasks.await(FirebaseMessaging.getInstance().token) }.getOrNull()
        } ?: BalajiMessagingService.cachedToken(context) ?: return false

        return withContext(Dispatchers.IO) {
            runCatching {
                api.upsertDeviceToken(
                    apiKey = BuildConfig.SUPABASE_ANON_KEY,
                    auth = "Bearer ${session.accessToken}",
                    body = DeviceTokenBody(
                        user_id = session.userId,
                        token = token,
                        app_flavour = if (BuildConfig.IS_STAFF) "staff" else "customer"
                    )
                ).isSuccessful
            }.getOrDefault(false)
        }
    }
}
