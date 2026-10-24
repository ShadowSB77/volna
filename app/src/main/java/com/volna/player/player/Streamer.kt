package com.volna.player.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.volna.player.data.Http
import com.volna.player.data.Library
import com.volna.player.data.SoundCloud
import com.volna.player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class StreamMode { NONE, PLAYLIST, WAVE }

/**
 * Онлайн-очередь (плейлист или Волна). Ссылки на поток у SoundCloud временные,
 * поэтому получаем их «лениво» — держим готовыми только 2 следующих трека.
 * Если трек уже скачан, играем его из памяти даже в онлайн-режиме.
 */
@OptIn(UnstableApi::class)
object Streamer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pending = ArrayDeque<Track>()
    private var supplier: (suspend () -> List<Track>)? = null
    private var startJob: Job? = null
    private var topJob: Job? = null
    private val retried = HashSet<String>()

    /** Что уже звучало — чтобы Волна не повторялась. */
    val played = LinkedHashSet<Long>()

    private val _mode = MutableStateFlow(StreamMode.NONE)
    val mode: StateFlow<StreamMode> = _mode.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun isActive() = _mode.value != StreamMode.NONE

    fun init() {
        PlayerHolder.player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                PlayerHolder.trackFor(mediaItem?.mediaId)?.scId?.let { rememberPlayed(it) }
                if (isActive()) topUp()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (isActive()) {
                    recover()
                } else {
                    _error.value = "Не удалось воспроизвести трек (${error.errorCodeName})"
                }
            }
        })
    }

    private fun rememberPlayed(id: Long) {
        played.remove(id)
        played.add(id)
        while (played.size > 500) played.remove(played.first())
    }

    fun start(mode: StreamMode, tracks: List<Track>, more: (suspend () -> List<Track>)? = null) {
        stop()
        _mode.value = mode
        _error.value = null
        supplier = more
        pending.addAll(tracks.filter { !it.isPreviewOnly })
        val p = PlayerHolder.player
        p.clearMediaItems()
        startJob = scope.launch {
            _loading.value = true
            val first = nextResolved()
            _loading.value = false
            if (first == null) {
                if (_error.value == null) _error.value = "Не получилось ничего включить"
                _mode.value = StreamMode.NONE
                return@launch
            }
            p.setMediaItem(first)
            p.prepare()
            p.play()
            topUpInternal()
        }
    }

    fun stop() {
        startJob?.cancel()
        topJob?.cancel()
        pending.clear()
        supplier = null
        retried.clear()
        _loading.value = false
        _mode.value = StreamMode.NONE
    }

    fun next() {
        scope.launch {
            val p = PlayerHolder.player
            if (!p.hasNextMediaItem()) {
                _loading.value = true
                topUpInternal()
                _loading.value = false
            }
            if (p.hasNextMediaItem()) p.seekToNextMediaItem()
        }
    }

    private fun topUp() {
        if (topJob?.isActive == true) return
        topJob = scope.launch { topUpInternal() }
    }

    private suspend fun topUpInternal() {
        val p = PlayerHolder.player
        while (isActive() && p.mediaItemCount - p.currentMediaItemIndex - 1 < 2) {
            val item = nextResolved() ?: break
            p.addMediaItem(item)
        }
    }

    private suspend fun nextResolved(): MediaItem? {
        repeat(10) {
            if (pending.isEmpty()) {
                val s = supplier ?: return null
                val more = try {
                    s()
                } catch (e: Exception) {
                    _error.value = Http.friendly(e)
                    emptyList()
                }
                if (more.isEmpty()) return null
                pending.addAll(more)
            }
            val t = pending.removeFirst()
            val item = resolve(t)
            if (item != null) return item
        }
        return null
    }

    private suspend fun resolve(t: Track): MediaItem? {
        val local = t.localPath?.let { t } ?: Library.find(t.scId)
        if (local?.localPath != null && File(local.localPath).exists()) {
            return PlayerHolder.mediaItem(local, Uri.fromFile(File(local.localPath)).toString(), null)
        }
        val id = t.scId ?: return null
        return try {
            val s = SoundCloud.stream(id)
            PlayerHolder.mediaItem(t, s.url, if (s.hls) MimeTypes.APPLICATION_M3U8 else null)
        } catch (e: Exception) {
            _error.value = Http.friendly(e)
            null
        }
    }

    /** Ссылка протухла или сеть моргнула — пробуем получить свежую, иначе пропускаем трек. */
    private fun recover() {
        val p = PlayerHolder.player
        val idx = p.currentMediaItemIndex
        val item = p.currentMediaItem ?: return
        val t = PlayerHolder.trackFor(item.mediaId) ?: return
        scope.launch {
            val fresh = if (retried.add(item.mediaId)) resolve(t) else null
            if (fresh != null) {
                p.replaceMediaItem(idx, fresh)
                p.prepare()
                p.play()
                return@launch
            }
            if (!p.hasNextMediaItem()) topUpInternal()
            if (p.hasNextMediaItem()) {
                p.seekToNextMediaItem()
                p.prepare()
                p.play()
            }
        }
    }
}
