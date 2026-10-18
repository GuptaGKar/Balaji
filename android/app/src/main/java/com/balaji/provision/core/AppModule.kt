package com.balaji.provision.core

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.rxjava3.RxDataStore
import com.google.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.Singleton
import dagger.hilt.android.scopes.ActivityScoped
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import java.io.File

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideAppDataStore(@ApplicationContext context: Context): RxDataStore<Preferences> =
        RxDataStoreBuilder(context, "app_prefs").build()
    
    @Provides
    @Singleton
    fun provideCartVersionKey(): Preferences.Key<Int> = preferencesKey("cart_version")
    
    @Provides
    @Singleton
    fun provideUserSessionKey(): Preferences.Key<String?> = preferencesKey("user_session")
    
    @Provides
    @Singleton
    fun provideAuthTokenKey(): Preferences.Key<String?> = preferencesKey("auth_token")
}