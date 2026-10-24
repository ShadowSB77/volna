package com.volna.player.player

import com.volna.player.data.Library
import com.volna.player.data.Track
import com.volna.player.data.SoundCloud
import java.io.IOException
import kotlin.random.Random

/**
 * Волна — бесконечный поток похожей музыки.
 * Берём «семя» (лайк, трек из медиатеки или из загруженного плейлиста,
 * иногда — только что сыгравший трек, чтобы поток плавно «дрейфовал»),
 * запрашиваем похожие треки и отсеиваем уже сыгранные и дизлайкнутые.
 */
object Wave {
    private var lastSeed: Long? = null

    fun hasSeeds(): Boolean {
        val (liked, other) = Library.waveSeeds()
        return liked.isNotEmpty() || other.isNotEmpty()
    }

    fun start() {
        Streamer.start(StreamMode.WAVE, emptyList()) { nextBatch() }
    }

    private fun pickSeed(): Long {
        val (liked, other) = Library.waveSeeds()
        val recent = Streamer.played.toList().takeLast(5)
        val r = Random.nextFloat()
        return when {
            recent.isNotEmpty() && r < 0.35f -> recent.random()
            liked.isNotEmpty() && r < 0.80f -> liked.random()
            other.isNotEmpty() -> other.random()
            liked.isNotEmpty() -> liked.random()
            recent.isNotEmpty() -> recent.random()
            else -> throw IOException("Сначала загрузи плейлист или лайкни пару треков")
        }
    }

    suspend fun nextBatch(): List<Track> {
        val disliked = Library.disliked.value
        repeat(4) {
            var seed = pickSeed()
            if (seed == lastSeed) seed = pickSeed()
            lastSeed = seed
            val fresh = SoundCloud.related(seed).filter { t ->
                val id = t.scId
                id != null && id !in Streamer.played && id !in disliked &&
                    !t.isPreviewOnly && t.durationMs in 60_000L..15 * 60_000L
            }
            if (fresh.isNotEmpty()) return fresh.shuffled().take(6)
        }
        return emptyList()
    }
}
