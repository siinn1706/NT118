package vn.uit.healthos.feature.leaderboard

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import vn.uit.healthos.core.data.db.AppDatabase
import vn.uit.healthos.core.network.DataEnvelope
import javax.inject.Singleton

@Serializable
data class LeaderboardEntryDto(
    val rank: Int,
    @SerialName("user_id") val userId: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val steps: Int,
    @SerialName("is_me") val isMe: Boolean = false,
)

@Serializable
data class LeaderboardDto(
    val metric: String = "steps",
    val period: String,
    val scope: String,
    val timezone: String = "",
    @SerialName("range_start") val rangeStart: String,
    @SerialName("range_end") val rangeEnd: String,
    val entries: List<LeaderboardEntryDto> = emptyList(),
    /** Null when the user chose to hide from leaderboards (or is not on the global board). */
    val me: LeaderboardEntryDto? = null,
    @SerialName("generated_at") val generatedAt: String = "",
)

/** Only the field this feature reads from `GET /v1/preferences/me`. */
@Serializable
data class LeaderboardPreferenceDto(
    @SerialName("leaderboard_visibility") val leaderboardVisibility: String = "friends",
)

interface LeaderboardApi {
    @GET("v1/leaderboard/steps")
    suspend fun steps(
        @Query("period") period: String,
        @Query("scope") scope: String,
        @Query("limit") limit: Int,
    ): DataEnvelope<LeaderboardDto>

    @GET("v1/preferences/me")
    suspend fun preferences(): DataEnvelope<LeaderboardPreferenceDto>
}

@Module
@InstallIn(SingletonComponent::class)
object LeaderboardNetworkModule {
    @Provides
    @Singleton
    fun provideLeaderboardApi(retrofit: Retrofit): LeaderboardApi = retrofit.create(LeaderboardApi::class.java)

    @Provides
    fun provideLeaderboardCacheDao(db: AppDatabase): LeaderboardCacheDao = db.leaderboardCacheDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LeaderboardBindModule {
    @Binds
    abstract fun bindLeaderboardRepository(impl: NetworkLeaderboardRepository): LeaderboardRepository
}
