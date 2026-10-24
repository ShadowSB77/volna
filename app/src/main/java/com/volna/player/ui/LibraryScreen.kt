package com.volna.player.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.volna.player.data.Downloader
import com.volna.player.data.Importer
import com.volna.player.data.Library
import com.volna.player.data.Track
import com.volna.player.player.PlayerHolder
import com.volna.player.player.StreamMode
import com.volna.player.player.Streamer
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by PlayerHolder.state.collectAsStateWithLifecycle()
    val tracks by Library.tracks.collectAsStateWithLifecycle()
    val liked by Library.liked.collectAsStateWithLifecycle()
    val states by Downloader.states.collectAsStateWithLifecycle()
    var showLiked by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var importing by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            importing = "Импортирую ${uris.size}…"
            scope.launch {
                val n = Importer.import(ctx, uris)
                importing = "Добавлено треков: $n"
            }
        }
    }

    val source = if (showLiked) liked else tracks
    val shown = remember(source, query) {
        if (query.isBlank()) source
        else source.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    }
    val downloadedIds = remember(tracks) { tracks.mapNotNull { it.scId }.toHashSet() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 24.dp),
    ) {
        item {
            SectionTitle("Медиатека", Modifier.padding(horizontal = 8.dp))
            Text(
                "${tracks.size} треков офлайн · играют без интернета",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { PlayerHolder.playLocal(tracks, 0, shuffle = true) },
                    enabled = tracks.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.Shuffle, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Перемешать")
                }
                FilledTonalButton(
                    onClick = { picker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Свои файлы")
                }
            }
            importing?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = !showLiked, onClick = { showLiked = false }, label = { Text("Скачанные") })
                FilterChip(
                    selected = showLiked, onClick = { showLiked = true },
                    label = { Text("Понравилось (${liked.size})") },
                    leadingIcon = { Icon(Icons.Rounded.Favorite, null) },
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Поиск по названию или артисту") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }

        if (shown.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (showLiked) "Лайкай треки в Волне или плеере — они появятся здесь"
                        else "Пока пусто. Загрузи плейлист и скачай доступные треки или добавь свои файлы.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        itemsIndexed(shown, key = { _, t -> t.key }) { i, t ->
            TrackRow(
                track = t,
                active = ui.track?.key == t.key,
                onClick = {
                    if (showLiked) {
                        // лайки играем онлайн (скачанные — из памяти)
                        Streamer.start(StreamMode.PLAYLIST, shown.drop(i))
                    } else {
                        PlayerHolder.playLocal(shown, i)
                    }
                },
                trailing = {
                    if (showLiked) {
                        DownloadBadge(t, t.scId != null && t.scId in downloadedIds, t.scId?.let { states[it] })
                    } else {
                        TrackMenu(t)
                    }
                },
            )
        }
    }
}

@Composable
private fun TrackMenu(t: Track) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "Ещё") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (t.scId != null) {
                DropdownMenuItem(
                    text = { Text(if (Library.isLiked(t.scId)) "Убрать из понравившихся" else "Нравится") },
                    leadingIcon = { Icon(Icons.Rounded.Favorite, null) },
                    onClick = { Library.toggleLike(t); open = false },
                )
            }
            DropdownMenuItem(
                text = { Text("Удалить с устройства") },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { Library.remove(t); open = false },
            )
        }
    }
}
