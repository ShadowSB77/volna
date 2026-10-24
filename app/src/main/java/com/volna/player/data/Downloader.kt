package com.volna.player.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

sealed interface DlState {
    data object Queued : DlState
    data class Progress(val p: Float) : DlState
    data object Done : DlState
    data class Failed(val msg: String) : DlState
}

/** Скачивает в память приложения треки, которые автор разрешил скачивать. */
object Downloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permits = Semaphore(3)
    private val active = AtomicInteger(0)
    private lateinit var app: Context

    private val _states = MutableStateFlow<Map<Long, DlState>>(emptyMap())
    val states: StateFlow<Map<Long, DlState>> = _states.asStateFlow()

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    private fun set(id: Long, s: DlState) = _states.update { it + (id to s) }

    fun canDownload(t: Track): Boolean =
        t.scId != null && t.downloadable && !t.isPreviewOnly && Library.find(t.scId) == null

    /** Возвращает, сколько треков поставлено в очередь. */
    fun enqueue(tracks: List<Track>): Int {
        val now = _states.value
        val list = tracks.filter { t ->
            canDownload(t) && now[t.scId] !is DlState.Progress && now[t.scId] != DlState.Queued
        }.distinctBy { it.scId }
        if (list.isEmpty()) return 0
        DownloadService.start(app)
        for (t in list) {
            val id = t.scId ?: continue
            set(id, DlState.Queued)
            active.incrementAndGet()
            scope.launch {
                try {
                    permits.withPermit { download(t, id) }
                } finally {
                    if (active.decrementAndGet() == 0) DownloadService.stop(app)
                }
            }
        }
        return list.size
    }

    private suspend fun download(t: Track, id: Long) {
        var tmp: File? = null
        try {
            set(id, DlState.Progress(0f))
            val url = SoundCloud.downloadUrl(id)
            val c = Http.open(url)
            try {
                val code = c.responseCode
                if (code !in 200..299) throw Http.HttpException(code)
                val ext = extension(c.getHeaderField("Content-Disposition"), c.contentType, url)
                val total = c.contentLengthLong
                val part = File(Library.musicDir(), "sc_$id.part")
                tmp = part
                var done = 0L
                var lastShown = -1
                c.inputStream.use { input ->
                    part.outputStream().buffered(128 * 1024).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastShown) {
                                    lastShown = pct
                                    set(id, DlState.Progress(pct / 100f))
                                }
                            }
                        }
                    }
                }
                val target = File(Library.musicDir(), "sc_$id.$ext")
                if (!part.renameTo(target)) throw java.io.IOException("Не удалось сохранить файл")
                tmp = null

                val art = saveArtwork(t.artworkUrl, id)
                Library.add(
                    t.copy(
                        localPath = target.absolutePath,
                        artworkUrl = art ?: t.artworkUrl,
                        addedAt = System.currentTimeMillis(),
                    )
                )
                set(id, DlState.Done)
            } finally {
                c.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            tmp?.delete()
            val msg = if (e is Http.HttpException && (e.code == 401 || e.code == 403 || e.code == 404)) {
                "Автор закрыл скачивание"
            } else Http.friendly(e)
            set(id, DlState.Failed(msg))
        }
    }

    /** Обложку тоже кладём локально, чтобы она была видна офлайн. */
    private fun saveArtwork(url: String?, id: Long): String? {
        if (url == null || !url.startsWith("http")) return null
        return try {
            val f = File(Library.artDir(), "sc_$id.jpg")
            val c = Http.open(url)
            try {
                if (c.responseCode !in 200..299) return null
                c.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            } finally {
                c.disconnect()
            }
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun extension(disposition: String?, contentType: String?, url: String): String {
        val fromName = disposition?.let {
            Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)").find(it)?.groupValues?.get(1)
        }?.substringAfterLast('.', "")?.lowercase()
        if (!fromName.isNullOrBlank() && fromName.length in 2..5) return fromName
        val ct = contentType?.lowercase() ?: ""
        return when {
            "mpeg" in ct || "mp3" in ct -> "mp3"
            "wav" in ct -> "wav"
            "flac" in ct -> "flac"
            "aif" in ct -> "aiff"
            "mp4" in ct || "m4a" in ct || "aac" in ct -> "m4a"
            "ogg" in ct -> "ogg"
            else -> url.substringBefore('?').substringAfterLast('.', "mp3").lowercase().takeIf { it.length in 2..5 } ?: "mp3"
        }
    }
}
