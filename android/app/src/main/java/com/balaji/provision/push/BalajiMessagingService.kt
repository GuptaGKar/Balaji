package com.balaji.provision.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM messages. A background message is rendered by the system from the
 * notification payload; a message that arrives while the app is in the foreground
 * (or one that carries only data) is posted here through [PushChannels].
 *
 * The token is cached locally so the next sign-in can upload it; uploading needs an
 * authenticated session, which may not exist yet when the token is issued.
 */
@AndroidEntryPoint
class BalajiMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushRegistrar: PushRegistrar

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        prefs(this).edit().putString(KEY_TOKEN, token).apply()
        // If someone is already signed in, register it now instead of waiting for login.
        scope.launch { runCatching { pushRegistrar.sync() } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        PushChannels.ensure(this)
        val data = message.data
        val title = message.notification?.title ?: data["title"]
        val body = message.notification?.body ?: data["body"]
        PushChannels.show(this, title, body, data)
    }

    companion object {
        private const val PREFS = "push_prefs"
        private const val KEY_TOKEN = "fcm_token"

        fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** Last token seen by the app, or null before the first FCM callback. */
        fun cachedToken(context: Context): String? =
            prefs(context).getString(KEY_TOKEN, null)
    }
}
