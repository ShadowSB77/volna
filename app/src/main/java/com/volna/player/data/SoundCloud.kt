package com.volna.player.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Работа с публичным веб-API SoundCloud:
 *  - разбор ссылок на плейлисты / треки / профили;
 *  - обычный поток для прослушивания онлайн (тот же, что слышит гость на сайте);
 *  - скачивание только тех треков, где автор сам включил кнопку Download.
 * Превью Go+ и зашифрованные потоки не трогаем.
 */
object SoundCloud {
    private const val API = "https://api-v2.soundcloud.com"

    data class Playlist(
        val title: String,
        val author: String,
        val artworkUrl: String?,
        val tracks: List<Track>,
    )

    data class Stream(val url: String, val hls: Boolean)

    private lateinit var prefs: SharedPreferences
    private val mutex = Mutex()
    @Volatile
    private var cachedId: String? = null

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("soundcloud", Context.MODE_PRIVATE)
        cachedId = prefs.getString("client_id", null)
    }

    // ---------- client_id ----------

    private suspend fun clientId(force: Boolean = false): String = mutex.withLock {
        val current = cachedId
        if (!force && current != null) {
            current
        } else {
            val fresh = withContext(Dispatchers.IO) { fetchClientId() }
            cachedId = fresh
            prefs.edit().putString("client_id", fresh).apply()
            fresh
        }
    }

    private fun fetchClientId(): String {
        val html = Http.getText("https://soundcloud.com/")
        val scripts = Regex("src=\"(https://a-v2\\.sndcdn\\.com/assets/[^\"]+\\.js)")
            .findAll(html).map { it.groupValues[1] }.toList()
        val re = Regex("client_id\\s*[:=]\\s*\"?([a-zA-Z0-9]{32})")
        for (s in scripts.asReversed()) {
            val js = try {
                Http.getText(s)
            } catch (e: Exception) {
                continue
            }
            val m = re.find(js)
            if (m != null) return m.groupValues[1]
        }
        throw IOException("Не удалось подключиться к SoundCloud")
    }

    private fun withCid(pathOrUrl: String, cid: String): String {
        val base = if (pathOrUrl.startsWith("http")) pathOrUrl else API + pathOrUrl
        val sep = if (base.contains('?')) "&" else "?"
        return base + sep + "client_id=" + cid
    }

    private suspend fun apiRaw(pathOrUrl: String): String = withContext(Dispatchers.IO) {
        val cid = clientId()
        try {
            Http.getText(withCid(pathOrUrl, cid))
        } catch (e: Http.HttpException) {
            if (e.code == 401 || e.code == 403) {
                Http.getText(withCid(pathOrUrl, clientId(force = true)))
            } else throw e
        }
    }

    // ---------- разбор ссылок ----------

    suspend fun resolve(input: String): Playlist {
        var url = Regex("https?://\\S+").find(input.trim())?.value ?: input.trim()
        if (!url.contains("soundcloud.com") && !url.contains("snd.sc")) {
            throw IOException("Это не ссылка SoundCloud")
        }
        if (url.contains("on.soundcloud.com") || url.contains("snd.sc")) {
            url = withContext(Dispatchers.IO) { Http.finalUrl(url) }
        }
        url = url.replace("://m.soundcloud.com", "://soundcloud.com").substringBefore('?')

        val obj = JSONObject(apiRaw("/resolve?url=" + URLEncoder.encode(url, "UTF-8")))
        return when (obj.optString("kind")) {
            "playlist", "system-playlist" -> {
                val user = obj.optJSONObject("user")
                Playlist(
                    title = obj.optString("title"),
                    author = user?.str("username") ?: "",
                    artworkUrl = bigArt(obj.str("artwork_url") ?: user?.str("avatar_url")),
                    tracks = fullTracks(obj.optJSONArray("tracks") ?: JSONArray()),
                )
            }
            "track" -> {
                val t = parseTrack(obj)
                Playlist(t.title, t.artist, t.artworkUrl, listOf(t))
            }
            "user" -> {
                val id = obj.getLong("id")
                val res = JSONObject(apiRaw("/users/$id/tracks?limit=200"))
                val arr = res.optJSONArray("collection") ?: JSONArray()
                Playlist(
                    title = obj.optString("username"),
                    author = "Треки исполнителя",
                    artworkUrl = bigArt(obj.str("avatar_url")),
                    tracks = (0 until arr.length()).map { parseTrack(arr.getJSONObject(it)) },
                )
            }
            else -> throw IOException("Эта ссылка не похожа на плейлист или трек")
        }
    }

    /** В ответе плейлиста полными приходят только первые треки, остальные — заглушки с id. */
    private suspend fun fullTracks(arr: JSONArray): List<Track> {
        val ids = ArrayList<Long>(arr.length())
        val full = HashMap<Long, JSONObject>()
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            val id = t.getLong("id")
            ids += id
            if (t.has("title")) full[id] = t
        }
        val missing = ids.filter { it !in full }
        for (chunk in missing.chunked(50)) {
            val res = JSONArray(apiRaw("/tracks?ids=" + chunk.joinToString(",")))
            for (j in 0 until res.length()) {
                val t = res.getJSONObject(j)
                full[t.getLong("id")] = t
            }
        }
        return ids.mapNotNull { id -> full[id]?.let { parseTrack(it) } }
    }

    fun parseTrack(o: JSONObject): Track {
        val user = o.optJSONObject("user")
        val id = o.getLong("id")
        return Track(
            key = "sc_$id",
            scId = id,
            title = o.optString("title"),
            artist = user?.str("username") ?: "",
            artworkUrl = bigArt(o.str("artwork_url") ?: user?.str("avatar_url")),
            durationMs = o.optLong("duration"),
            permalink = o.str("permalink_url"),
            downloadable = o.optBoolean("downloadable") && o.optBoolean("has_downloads_left", true),
            policy = o.str("policy"),
        )
    }

    private fun bigArt(url: String?): String? = url?.replace("-large.", "-t500x500.")

    // ---------- прослушивание ----------

    suspend fun stream(id: Long): Stream {
        val o = JSONObject(apiRaw("/tracks/$id"))
        when (o.optString("policy")) {
            "BLOCK" -> throw IOException("Трек недоступен")
            "SNIP" -> throw IOException("Это превью Go+, полный трек недоступен")
        }
        val auth = o.str("track_authorization")
        val list = o.optJSONObject("media")?.optJSONArray("transcodings")
            ?: throw IOException("У трека нет потока")

        var bestUrl: String? = null
        var bestHls = false
        var bestScore = -1
        for (i in 0 until list.length()) {
            val t = list.getJSONObject(i)
            if (t.optBoolean("snipped")) continue
            val fmt = t.optJSONObject("format") ?: continue
            val protocol = fmt.optString("protocol")
            val mime = fmt.optString("mime_type")
            val score = when {
                protocol == "progressive" -> 3
                protocol == "hls" && mime.startsWith("audio/mpeg") -> 2
                protocol == "hls" && mime.startsWith("audio/mp4") -> 1
                else -> -1 // зашифрованные и opus-потоки пропускаем
            }
            if (score > bestScore) {
                bestScore = score
                bestUrl = t.str("url")
                bestHls = protocol == "hls"
            }
        }
        val u = bestUrl ?: throw IOException("Поток недоступен")
        val withAuth = if (auth != null) u + (if (u.contains('?')) "&" else "?") + "track_authorization=" + auth else u
        val res = JSONObject(apiRaw(withAuth))
        return Stream(res.getString("url"), bestHls)
    }

    // ---------- скачивание (только разрешённое автором) ----------

    suspend fun downloadUrl(id: Long): String {
        val res = JSONObject(apiRaw("/tracks/$id/download"))
        return res.str("redirectUri") ?: throw IOException("Автор закрыл скачивание")
    }

    // ---------- рекомендации для Волны ----------

    suspend fun related(id: Long, limit: Int = 30): List<Track> {
        val res = JSONObject(apiRaw("/tracks/$id/related?limit=$limit"))
        val arr = res.optJSONArray("collection") ?: return emptyList()
        return (0 until arr.length()).mapNotNull {
            try {
                parseTrack(arr.getJSONObject(it))
            } catch (e: Exception) {
                null
            }
        }
    }
}
