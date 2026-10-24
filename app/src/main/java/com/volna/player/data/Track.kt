package com.volna.player.data

import org.json.JSONObject

/**
 * Один трек: и онлайн (из SoundCloud), и офлайн (скачанный / импортированный).
 * localPath != null — файл лежит внутри приложения и играет без интернета.
 */
data class Track(
    val key: String,
    val scId: Long?,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val permalink: String?,
    /** Автор сам разрешил скачивание (кнопка Download на SoundCloud). */
    val downloadable: Boolean,
    /** ALLOW / MONETIZE / SNIP (только превью) / BLOCK. */
    val policy: String?,
    val localPath: String? = null,
    val addedAt: Long = 0L,
) {
    val isLocal: Boolean get() = localPath != null
    val isPreviewOnly: Boolean get() = policy == "SNIP" || policy == "BLOCK"

    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        if (scId != null) put("scId", scId)
        put("title", title)
        put("artist", artist)
        if (artworkUrl != null) put("art", artworkUrl)
        put("dur", durationMs)
        if (permalink != null) put("link", permalink)
        put("dl", downloadable)
        if (policy != null) put("policy", policy)
        if (localPath != null) put("path", localPath)
        put("added", addedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Track = Track(
            key = o.getString("key"),
            scId = if (o.has("scId")) o.getLong("scId") else null,
            title = o.optString("title"),
            artist = o.optString("artist"),
            artworkUrl = o.str("art"),
            durationMs = o.optLong("dur"),
            permalink = o.str("link"),
            downloadable = o.optBoolean("dl"),
            policy = o.str("policy"),
            localPath = o.str("path"),
            addedAt = o.optLong("added"),
        )
    }
}

/** optString возвращает "null" для null-значений — этот хелпер возвращает настоящий null. */
fun JSONObject.str(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
