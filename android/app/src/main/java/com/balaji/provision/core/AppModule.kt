package com.balaji.provision.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.balaji.provision.data.auth.SessionStorage
import com.balaji.provision.data.auth.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideAppDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore
    
    @Provides
    @Singleton
    fun provideSessionStorage(store: SessionStore): SessionStorage = store

    @Provides
    @Singleton
    @Named("cartItemsKey")
    fun provideCartItemsKey(): Preferences.Key<String> = stringPreferencesKey("cart_items")
    
    @Provides
    @Singleton
    @Named("userSessionKey")
    fun provideUserSessionKey(): Preferences.Key<String> = stringPreferencesKey("user_session")
    
    @Provides
    @Singleton
    @Named("authTokenKey")
    fun provideAuthTokenKey(): Preferences.Key<String> = stringPreferencesKey("auth_token")

    @Provides
    @Singleton
    @Named("darkThemeKey")
    fun provideDarkThemeKey(): Preferences.Key<Boolean> = booleanPreferencesKey("customer_dark_theme")
}