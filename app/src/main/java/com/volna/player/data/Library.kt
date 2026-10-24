package com.volna.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Офлайн-медиатека: файлы лежат во внутренней памяти приложения (files/music),
 * список хранится в library.json. Плюс лайки/дизлайки и «семена» для Волны.
 */
object Library {
    private lateinit var root: File

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _liked = MutableStateFlow<List<Track>>(emptyList())
    val liked: StateFlow<List<Track>> = _liked.asStateFlow()

    private val _disliked = MutableStateFlow<Set<Long>>(emptySet())
    val disliked: StateFlow<Set<Long>> = _disliked.asStateFlow()

    /** id треков из загруженных плейлистов — от них Волна ищет похожее. */
    private var seeds: List<Long> = emptyList()

    fun init(ctx: Context) {
        root = ctx.filesDir
        load()
    }

    fun musicDir(): File = File(root, "music").apply { mkdirs() }
    fun artDir(): File = File(root, "art").apply { mkdirs() }

    fun find(scId: Long?): Track? =
        if (scId == null) null else _tracks.value.firstOrNull { it.scId == scId }

    @Synchronized
    fun add(t: Track) {
        _tracks.value = listOf(t) + _tracks.value.filter { it.key != t.key }
        save()
    }

    @Synchronized
    fun remove(t: Track) {
        t.localPath?.let { File(it).delete() }
        t.artworkUrl?.takeIf { it.startsWith("/") }?.let { File(it).delete() }
        _tracks.value = _tracks.value.filter { it.key != t.key }
        save()
    }

    fun isLiked(scId: Long?): Boolean = scId != null && _liked.value.any { it.scId == scId }

    @Synchronized
    fun toggleLike(t: Track) {
        val id = t.scId ?: return
        _liked.value = if (isLiked(id)) {
            _liked.value.filter { it.scId != id }
        } else {
            _disliked.value = _disliked.value - id
            listOf(t.copy(localPath = null)) + _liked.value
        }
        save()
    }

    @Synchronized
    fun dislike(t: Track) {
        val id = t.scId ?: return
        _disliked.value = _disliked.value + id
        _liked.value = _liked.value.filter { it.scId != id }
        save()
    }

    @Synchronized
    fun rememberSeeds(list: List<Track>) {
        val ids = list.mapNotNull { it.scId }
        seeds = (ids + seeds).distinct().take(400)
        save()
    }

    /** Все треки, от которых Волна может отталкиваться. Лайки важнее всего. */
    fun waveSeeds(): Pair<List<Long>, List<Long>> {
        val liked = _liked.value.mapNotNull { it.scId }
        val other = (_tracks.value.mapNotNull { it.scId } + seeds).distinct() - liked.toSet()
        return liked to other
    }

    // ---------- хранение ----------

    private fun file() = File(root, "library.json")

    private fun load() {
        try {
            val f = file()
            if (!f.exists()) return
            val o = JSONObject(f.readText())
            _tracks.value = o.optJSONArray("tracks").toTracks()
                .filter { it.localPath == null || File(it.localPath).exists() }
            _liked.value = o.optJSONArray("liked").toTracks()
            val d = o.optJSONArray("disliked")
            _disliked.value = if (d == null) emptySet() else (0 until d.length()).map { d.getLong(it) }.toSet()
            val s = o.optJSONArray("seeds")
            seeds = if (s == null) emptyList() else (0 until s.length()).map { s.getLong(it) }
        } catch (e: Exception) {
            // повреждённый файл — начинаем с пустой медиатеки
        }
    }

    private fun save() {
        val o = JSONObject()
        o.put("tracks", JSONArray().apply { _tracks.value.forEach { put(it.toJson()) } })
        o.put("liked", JSONArray().apply { _liked.value.forEach { put(it.toJson()) } })
        o.put("disliked", JSONArray().apply { _disliked.value.forEach { put(it) } })
        o.put("seeds", JSONArray().apply { seeds.forEach { put(it) } })
        val tmp = File(root, "library.json.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(file())) {
            file().writeText(o.toString())
            tmp.delete()
        }
    }

    private fun JSONArray?.toTracks(): List<Track> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull {
            try {
                Track.fromJson(getJSONObject(it))
            } catch (e: Exception) {
                null
            }
        }
    }
}
