package com.greencom.android.podcasts.player

import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.media.AudioAttributesCompat
import androidx.media2.common.MediaItem
import androidx.media2.common.MediaMetadata
import androidx.media2.common.SessionPlayer
import androidx.media2.common.UriMediaItem
import androidx.media2.player.MediaPlayer
import androidx.media2.session.MediaSession
import androidx.media2.session.MediaSessionService
import androidx.media2.session.SessionToken
import com.greencom.android.podcasts.utils.PLAYER_TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.Executors

// TODO
class PlayerService : MediaSessionService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)

    private lateinit var mediaSession: MediaSession
    private lateinit var player: MediaPlayer

    private val isPlaying: Boolean
        get() = player.playerState == MediaPlayer.PLAYER_STATE_PLAYING

    private val isPaused: Boolean
        get() = player.playerState == MediaPlayer.PLAYER_STATE_PAUSED

    private val audioAttrs: AudioAttributesCompat by lazy {
        AudioAttributesCompat.Builder()
            .setUsage(AudioAttributesCompat.USAGE_MEDIA)
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
            .build()
    }

    private val sessionCallback: MediaSession.SessionCallback by lazy {
        object : MediaSession.SessionCallback() {
            override fun onSetMediaUri(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                uri: Uri,
                extras: Bundle?
            ): Int {
                Log.d(PLAYER_TAG,"sessionCallback: onSetMediaUri()")
                resetPlayer()

                val mediaItemBuilder = UriMediaItem.Builder(uri)
                if (extras != null) {
                    mediaItemBuilder
                        .setMetadata(MediaMetadata.Builder()
                                .putString(EPISODE_ID, extras.getString(EPISODE_ID))
                                .putString(EPISODE_TITLE, extras.getString(EPISODE_TITLE))
                                .putString(EPISODE_PUBLISHER, extras.getString(EPISODE_PUBLISHER))
                                .putString(EPISODE_IMAGE, extras.getString(EPISODE_IMAGE))
                                .putLong(EPISODE_DURATION, extras.getLong(EPISODE_DURATION))
                                .build())
                        .setStartPosition(extras.getLong(EPISODE_START_POSITION))
                }

                val result = player.setMediaItem(mediaItemBuilder.build()).get()
                if (result.resultCode != SessionPlayer.PlayerResult.RESULT_SUCCESS) {
                    Log.d(PLAYER_TAG, "player.setMediaItem() ERROR ${result.resultCode}")
                }
                return result.resultCode
            }
        }
    }

    private val playerCallback: MediaPlayer.PlayerCallback by lazy {
        object : MediaPlayer.PlayerCallback() {
            override fun onError(mp: MediaPlayer, item: MediaItem, what: Int, extra: Int) {
                Log.d(PLAYER_TAG,"playerCallback: onError(), what $what, extra $extra")
                super.onError(mp, item, what, extra)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(PLAYER_TAG,"PlayerService.onCreate()")

        player = MediaPlayer(this).apply {
            registerPlayerCallback(Executors.newSingleThreadExecutor(), playerCallback)
            setAudioAttributes(audioAttrs)
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionCallback(Executors.newSingleThreadExecutor(), sessionCallback)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(PLAYER_TAG,"PlayerService.onStartCommand()")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        Log.d(PLAYER_TAG,"PlayerService.onBind()")
        return PlayerServiceBinder()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(PLAYER_TAG,"PlayerService.onUnbind()")
        stopSelf() // TODO: TEMPORARY
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(PLAYER_TAG,"PlayerService.onDestroy()")
        mediaSession.close()
        player.unregisterPlayerCallback(playerCallback)
        player.close()
        scope.cancel()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        Log.d(PLAYER_TAG,"PlayerService.onGetSession()")
        return mediaSession
    }

    private fun resetPlayer() {
        player.reset()
        player.setAudioAttributes(audioAttrs)
    }

    inner class PlayerServiceBinder : Binder() {
        val sessionToken: SessionToken
            get() = mediaSession.token
    }

    companion object {
        const val EPISODE_ID = MediaMetadata.METADATA_KEY_MEDIA_ID
        const val EPISODE_TITLE = MediaMetadata.METADATA_KEY_TITLE
        const val EPISODE_PUBLISHER = MediaMetadata.METADATA_KEY_AUTHOR
        const val EPISODE_IMAGE = MediaMetadata.METADATA_KEY_ART_URI
        const val EPISODE_DURATION = MediaMetadata.METADATA_KEY_DURATION
        const val EPISODE_START_POSITION = "EPISODE_START_POSITION"

        const val SKIP_FORWARD_VALUE = 30_000
        const val SKIP_BACKWARD_VALUE = 10_000
    }
}