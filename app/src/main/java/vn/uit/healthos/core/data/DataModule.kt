package vn.uit.healthos.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.work.WorkManager
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import vn.uit.healthos.core.data.db.AppDatabase
import vn.uit.healthos.core.data.db.UserDataDao
import vn.uit.healthos.core.data.outbox.OutboxDao
import vn.uit.healthos.core.data.session.EncryptedTokenStore
import vn.uit.healthos.core.data.session.TokenStore
import vn.uit.healthos.feature.home.DashboardCacheDao
import vn.uit.healthos.feature.hydration.HydrationDao
import vn.uit.healthos.feature.meals.PendingMealDao
import vn.uit.healthos.feature.meds.DoseCacheDao
import vn.uit.healthos.feature.steps.DailyStepDao
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAead(@ApplicationContext context: Context): Aead {
        AeadConfig.register()
        return AndroidKeysetManager.Builder()
            .withSharedPref(context, "healthos_keyset", "healthos_keyset_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://healthos_master_key")
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context, aead: Aead, json: Json): TokenStore =
        EncryptedTokenStore(context.sessionDataStore, aead, json)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "healthos.db")
            // Versions 1-3 only shipped as development builds and hold no data worth migrating.
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3)
            .build()

    @Provides
    fun provideUserDataDao(db: AppDatabase): UserDataDao = db.userDataDao()

    @Provides
    fun provideOutboxDao(db: AppDatabase): OutboxDao = db.outboxDao()

    @Provides
    fun provideDailyStepDao(db: AppDatabase): DailyStepDao = db.dailyStepDao()

    @Provides
    fun provideHydrationDao(db: AppDatabase): HydrationDao = db.hydrationDao()

    @Provides
    fun provideDashboardCacheDao(db: AppDatabase): DashboardCacheDao = db.dashboardCacheDao()

    @Provides
    fun providePendingMealDao(db: AppDatabase): PendingMealDao = db.pendingMealDao()

    @Provides
    fun provideDoseCacheDao(db: AppDatabase): DoseCacheDao = db.doseCacheDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
