package com.volna.player.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.volna.player.data.Downloader
import com.volna.player.data.Library
import com.volna.player.player.PlayerHolder
import com.volna.player.player.StreamMode
import com.volna.player.player.Streamer
import com.volna.player.player.Wave
import com.volna.player.ui.theme.VColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WaveScreen() {
    val ui by PlayerHolder.state.collectAsStateWithLifecycle()
    val mode by Streamer.mode.collectAsStateWithLifecycle()
    val loading by Streamer.loading.collectAsStateWithLifecycle()
    val error by Streamer.error.collectAsStateWithLifecycle()
    val liked by Library.liked.collectAsStateWithLifecycle()
    Library.tracks.collectAsStateWithLifecycle() // перерисовка, когда трек скачался

    val onWave = mode == StreamMode.WAVE
    val playing = onWave && ui.isPlaying
    val track = if (onWave) ui.track else null

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Волна",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Бесконечный поток музыки под твой вкус. Лайкай — и Волна подстраивается.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )

        Box(
            Modifier.fillMaxWidth(0.86f).aspectRatio(1f).padding(top = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            WaveOrb(active = playing, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.size(92.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable {
                        when {
                            onWave -> PlayerHolder.toggle()
                            Wave.hasSeeds() -> Wave.start()
                            else -> Wave.start() // покажет понятную ошибку
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                } else {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "Пауза" else "Запустить Волну",
                        tint = Color.White,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
        }

        if (track != null) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                track.artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            val isLiked = liked.any { it.scId == track.scId }
            val canDl = Downloader.canDownload(track)
            Row(
                Modifier.padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { Library.dislike(track); PlayerHolder.next() },
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Rounded.ThumbDown, "Не нравится") }
                FilledTonalIconButton(
                    onClick = { Library.toggleLike(track) },
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Нравится",
                        tint = if (isLiked) VColors.Pink else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(30.dp),
                    )
                }
                FilledTonalIconButton(
                    onClick = { PlayerHolder.next() },
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Rounded.SkipNext, "Дальше") }
                if (canDl) {
                    FilledTonalIconButton(
                        onClick = { Downloader.enqueue(listOf(track)) },
                        modifier = Modifier.size(56.dp),
                    ) { Icon(Icons.Rounded.Download, "Скачать") }
                }
            }
        } else {
            Text(
                if (Wave.hasSeeds()) "Нажми, чтобы запустить"
                else "Для старта загрузи свой плейлист во вкладке «Плейлист» — Волна начнёт от него",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        error?.let {
            if (!playing) {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Переливающийся шар: крутится и «дышит» быстрее, когда играет музыка. */
@Composable
fun WaveOrb(active: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "orb")
    val rot by t.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (active) 8000 else 20000, easing = LinearEasing)),
        label = "rot",
    )
    val pulse by t.animateFloat(
        initialValue = 0.92f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            tween(if (active) 900 else 3000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Canvas(modifier) {
        val r = size.minDimension / 2f * pulse
        val c = center
        val blobs = listOf(VColors.Violet to 0f, VColors.Pink to 120f, VColors.Amber to 240f)
        for ((color, offset) in blobs) {
            val a = Math.toRadians((rot + offset).toDouble())
            val o = Offset(c.x + cos(a).toFloat() * r * 0.3f, c.y + sin(a).toFloat() * r * 0.3f)
            drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = 0.9f), Color.Transparent), center = o, radius = r * 0.85f),
                radius = r * 0.85f,
                center = o,
                blendMode = BlendMode.Screen,
            )
        }
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), center = c, radius = r * 0.55f),
            radius = r * 0.55f,
            center = c,
        )
    }
}
