package com.volna.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.volna.player.data.DlState
import com.volna.player.data.Downloader
import com.volna.player.data.Track
import com.volna.player.ui.theme.VColors
import java.io.File

fun artModel(url: String?): Any? = when {
    url == null -> null
    url.startsWith("/") -> File(url)
    else -> url
}

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun Artwork(url: String?, modifier: Modifier = Modifier, corner: Dp = 10.dp) {
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(VColors.Brand),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MusicNote, null,
            tint = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.fillMaxSize(0.4f),
        )
        if (url != null) {
            AsyncImage(
                model = artModel(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    active: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Artwork(track.artworkUrl, Modifier.size(52.dp))
            if (active) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = Color.White)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (track.durationMs > 0) "${track.artist} · ${fmtTime(track.durationMs)}" else track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

/** Значок состояния трека: скачан / качается / можно скачать / только онлайн / превью. */
@Composable
fun DownloadBadge(track: Track, downloaded: Boolean, state: DlState?) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val box = Modifier.padding(12.dp).size(24.dp)
    when {
        downloaded || track.isLocal ->
            Icon(Icons.Rounded.CheckCircle, "Скачан", tint = MaterialTheme.colorScheme.primary, modifier = box)
        state is DlState.Progress ->
            CircularProgressIndicator(progress = { state.p }, modifier = box, strokeWidth = 2.5.dp)
        state == DlState.Queued ->
            CircularProgressIndicator(modifier = box, strokeWidth = 2.5.dp)
        state is DlState.Failed ->
            IconButton(onClick = { Downloader.enqueue(listOf(track)) }) {
                Icon(Icons.Rounded.ErrorOutline, state.msg, tint = MaterialTheme.colorScheme.error)
            }
        track.isPreviewOnly ->
            Icon(Icons.Rounded.Lock, "Только превью", tint = dim, modifier = box)
        track.downloadable ->
            IconButton(onClick = { Downloader.enqueue(listOf(track)) }) {
                Icon(Icons.Rounded.Download, "Скачать", tint = MaterialTheme.colorScheme.onSurface)
            }
        else ->
            Icon(Icons.Rounded.CloudQueue, "Только онлайн", tint = dim, modifier = box)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}
