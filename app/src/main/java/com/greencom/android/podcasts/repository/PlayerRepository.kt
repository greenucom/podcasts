package com.greencom.android.podcasts.repository

import com.greencom.android.podcasts.data.domain.Episode
import kotlinx.coroutines.flow.Flow

/** Interface that defines player repository that contains player-related use cases. */
interface PlayerRepository {

    /** Get the episode by ID. */
    suspend fun getEpisode(episodeId: String): Episode?

    /** Get the episode's last position by ID. */
    suspend fun getEpisodePosition(episodeId: String): Long?

    /** Save the ID of the episode that was last played by the player to the DataStore. */
    suspend fun setLastEpisodeId(episodeId: String)

    /** Get Flow with the ID of the episode that was last played by the player from the DataStore. */
    fun getLastEpisodeId(): Flow<String?>

    /** Update episode state depending on the last position. */
    suspend fun updateEpisodeState(episodeId: String, position: Long, duration: Long)

    /** Mark episode completed by ID. */
    suspend fun markEpisodeCompleted(episodeId: String)
}