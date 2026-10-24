package com.volna.player.player

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.volna.player.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class PlayerUi(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

/** Единственный на всё приложение плеер. UI и сервис уведомлений работают с ним напрямую. */
@OptIn(UnstableApi::class)
object PlayerHolder {
    lateinit var player: ExoPlayer
        private set

    private val tracks = ConcurrentHashMap<String, Track>()

    private val _state = MutableStateFlow(PlayerUi())
    val state: StateFlow<PlayerUi> = _state.asStateFlow()

    fun init(ctx: Context) {
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player = ExoPlayer.Builder(ctx)
            .setAudioAttributes(attrs, true)       // пауза при звонке / другом плеере
            .setHandleAudioBecomingNoisy(true)     // пауза, когда вынул наушники
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val sessionId = ctx.getSystemService(AudioManager::class.java).generateAudioSessionId()
        player.setAudioSessionId(sessionId)
        AudioFx.attach(ctx, sessionId)

        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) = publish()
        })
    }

    fun trackFor(mediaId: String?): Track? = mediaId?.let { tracks[it] }

    private fun publish() {
        val p = player
        _state.value = PlayerUi(
            track = trackFor(p.currentMediaItem?.mediaId),
            isPlaying = p.isPlaying,
            buffering = p.playbackState == Player.STATE_BUFFERING,
            durationMs = p.duration.coerceAtLeast(0L),
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
        )
    }

    fun artUri(art: String?): Uri? = when {
        art == null -> null
        art.startsWith("/") -> Uri.fromFile(File(art))
        else -> Uri.parse(art)
    }

    fun mediaItem(t: Track, uri: String, mimeType: String?): MediaItem {
        tracks[t.key] = t
        val meta = MediaMetadata.Builder()
            .setTitle(t.title)
            .setArtist(t.artist)
            .setArtworkUri(artUri(t.artworkUrl))
            .build()
        val b = MediaItem.Builder()
            .setMediaId(t.key)
            .setUri(uri)
            .setMediaMetadata(meta)
        if (mimeType != null) b.setMimeType(mimeType)
        return b.build()
    }

    /** Офлайн-воспроизведение из медиатеки. */
    fun playLocal(list: List<Track>, start: Int, shuffle: Boolean = false) {
        val playable = list.filter { it.localPath != null && File(it.localPath).exists() }
        if (playable.isEmpty()) return
        Streamer.stop()
        val startKey = list.getOrNull(start)?.key
        val idx = playable.indexOfFirst { it.key == startKey }.coerceAtLeast(0)
        val items = playable.map { mediaItem(it, Uri.fromFile(File(it.localPath!!)).toString(), null) }
        player.shuffleModeEnabled = shuffle
        player.setMediaItems(items, if (shuffle) (items.indices).random() else idx, 0L)
        player.prepare()
        player.play()
    }

    fun toggle() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            player.play()
        }
    }

    fun next() {
        if (Streamer.isActive()) Streamer.next()
        else if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun previous() {
        if (player.currentPosition > 4000 || !player.hasPreviousMediaItem()) player.seekTo(0L)
        else player.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}
