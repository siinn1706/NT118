package vn.uit.healthos.feature.safety

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import vn.uit.healthos.core.data.session.UserDataCleanup

@Module
@InstallIn(SingletonComponent::class)
object SafetyCleanupModule {
    @Provides
    @IntoSet
    fun provideSafetyCleanup(controller: SosController): UserDataCleanup = UserDataCleanup { controller.clearUserData() }
}
