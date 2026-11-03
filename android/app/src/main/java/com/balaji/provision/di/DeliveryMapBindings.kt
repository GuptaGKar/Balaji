package com.balaji.provision.di

import com.balaji.provision.data.location.LocationFixSource
import com.balaji.provision.data.location.LocationRepository
import com.balaji.provision.data.repository.AreaSuggestionRepository
import com.balaji.provision.data.repository.AreaSuggestionSource
import com.balaji.provision.data.repository.DeliveryAreaRepository
import com.balaji.provision.data.repository.DeliveryAreaSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The delivery pin picker depends on behaviour rather than on the Android location
 * client, so the same view model can be exercised in a JVM test with a fake fix source.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DeliveryMapBindings {

    @Binds
    @Singleton
    abstract fun bindLocationFixSource(impl: LocationRepository): LocationFixSource

    @Binds
    @Singleton
    abstract fun bindDeliveryAreaSource(impl: DeliveryAreaRepository): DeliveryAreaSource

    /** Suggestions are data, not a device capability: the same seam keeps the debounce testable. */
    @Binds
    @Singleton
    abstract fun bindAreaSuggestionSource(impl: AreaSuggestionRepository): AreaSuggestionSource
}
