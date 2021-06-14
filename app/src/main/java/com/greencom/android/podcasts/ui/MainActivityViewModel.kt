package com.greencom.android.podcasts.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.media2.session.SessionToken
import com.greencom.android.podcasts.player.PlayerServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import kotlin.time.ExperimentalTime

// TODO
@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val player: PlayerServiceConnection,
) : ViewModel() {

    val currentEpisode: StateFlow<PlayerServiceConnection.CurrentEpisode>
        get() = player.currentEpisode

    val playerState: StateFlow<Int>
        get() = player.playerState

    val currentPosition: StateFlow<Long>
        get() = player.currentPosition

    val isPlaying: Boolean
        get() = player.isPlaying

    val isPaused: Boolean
        get() = player.isPaused

    val duration: Long
        get() = player.duration

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun skipForward() {
        player.skipForward()
    }

    fun skipBackward() {
        player.skipBackward()
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
    }

    fun createMediaController(context: Context, sessionToken: SessionToken) {
        player.createMediaController(context, sessionToken)
    }

    override fun onCleared() {
        super.onCleared()
        player.close()
    }
}