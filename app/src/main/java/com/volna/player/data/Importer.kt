package com.volna.player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Импорт своих аудиофайлов с телефона в медиатеку приложения. */
object Importer {

    suspend fun import(ctx: Context, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (uri in uris) {
            try {
                if (importOne(ctx, uri)) count++
            } catch (e: Exception) {
                // пропускаем битый файл
            }
        }
        count
    }

    private fun importOne(ctx: Context, uri: Uri): Boolean {
        val cr = ctx.contentResolver
        val name = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "track"
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(cr.getType(uri))
            ?: "mp3"

        val key = "loc_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val file = File(Library.musicDir(), "$key.$ext")
        val input = cr.openInputStream(uri) ?: return false
        input.use { i -> file.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }

        var title = name.substringBeforeLast('.')
        var artist = "Неизвестный исполнитель"
        var duration = 0L
        var art: String? = null
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { title = it }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }?.let { artist = it }
            duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val pic = mmr.embeddedPicture
            if (pic != null) {
                val f = File(Library.artDir(), "$key.jpg")
                f.writeBytes(pic)
                art = f.absolutePath
            }
        } catch (e: Exception) {
            // метаданных нет — оставляем имя файла
        } finally {
            try {
                mmr.release()
            } catch (e: Exception) {
            }
        }

        Library.add(
            Track(
                key = key,
                scId = null,
                title = title,
                artist = artist,
                artworkUrl = art,
                durationMs = duration,
                permalink = null,
                downloadable = false,
                policy = null,
                localPath = file.absolutePath,
                addedAt = System.currentTimeMillis(),
            )
        )
        return true
    }
}
