package com.volna.player.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

private enum class LibTab(val title: String) { DOWNLOADED("Скачанные"), LIKED("Понравилось"), PLAYLISTS("Плейлисты") }

/** Играет список: скачанные — из памяти, остальные — онлайн. */
private fun playList(list: List<Track>, index: Int) {
    if (list.all { it.isLocal }) PlayerHolder.playLocal(list, index)
    else Streamer.start(StreamMode.PLAYLIST, list.drop(index))
}

@Composable
fun LibraryScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by PlayerHolder.state.collectAsStateWithLifecycle()
    val tracks by Library.tracks.collectAsStateWithLifecycle()
    val liked by Library.liked.collectAsStateWithLifecycle()
    val playlists by Library.playlists.collectAsStateWithLifecycle()
    val states by Downloader.states.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(LibTab.DOWNLOADED) }
    var openPlaylist by rememberSaveable { mutableStateOf<String?>(null) }
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

    val opened = playlists.firstOrNull { it.id == openPlaylist }
    BackHandler(enabled = opened != null) { openPlaylist = null }

    val source: List<Track> = when {
        opened != null -> opened.tracks
        tab == LibTab.LIKED -> liked
        tab == LibTab.DOWNLOADED -> tracks
        else -> emptyList()
    }
    val shown = remember(source, query) {
        if (query.isBlank()) source
        else source.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    }
    val downloadedIds = remember(tracks) { tracks.mapNotNull { it.scId }.toHashSet() }
    // для онлайн-списков подставляем скачанную версию, если она есть
    val byId = remember(tracks) { tracks.filter { it.scId != null }.associateBy { it.scId } }
    val playable = remember(shown, byId) { shown.map { t -> byId[t.scId] ?: t } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 24.dp),
    ) {
        if (opened != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { openPlaylist = null }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                    Text("Медиатека", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(opened.artworkUrl, Modifier.size(96.dp), corner = 16.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            opened.title, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(opened.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${opened.tracks.size} треков",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { playList(playable, 0) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Слушать")
                    }
                    FilledTonalButton(
                        onClick = { playList(playable.shuffled(), 0) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Shuffle, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Перемешать")
                    }
                }
            }
        } else {
            item {
                SectionTitle("Медиатека", Modifier.padding(horizontal = 8.dp))
                Text(
                    "${tracks.size} скачано · ${liked.size} понравилось · ${playlists.size} плейлистов",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    items(LibTab.entries.toList()) { t ->
                        FilterChip(
                            selected = tab == t,
                            onClick = { tab = t },
                            label = { Text(t.title) },
                            leadingIcon = {
                                Icon(
                                    when (t) {
                                        LibTab.DOWNLOADED -> Icons.Rounded.DownloadDone
                                        LibTab.LIKED -> Icons.Rounded.Favorite
                                        LibTab.PLAYLISTS -> Icons.AutoMirrored.Rounded.QueueMusic
                                    },
                                    null,
                                )
                            },
                        )
                    }
                }
                if (tab != LibTab.PLAYLISTS) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { playList(playable.shuffled(), 0) },
                            enabled = playable.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.Shuffle, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Перемешать")
                        }
                        if (tab == LibTab.DOWNLOADED) {
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
                    }
                    importing?.let {
                        Text(
                            it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp),
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
            }
        }

        if (opened == null && tab == LibTab.PLAYLISTS) {
            if (playlists.isEmpty()) {
                item {
                    EmptyNote("Здесь будут твои плейлисты. Загрузи плейлист во вкладке «Плейлист» и нажми «Сохранить плейлист в Медиатеку».")
                }
            }
            items(playlists, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { query = ""; openPlaylist = p.id }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(p.artworkUrl, Modifier.size(64.dp), corner = 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${p.author} · ${p.tracks.size} треков",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    PlaylistMenu(p.id)
                }
            }
        } else {
            if (shown.isEmpty()) {
                item {
                    EmptyNote(
                        when {
                            opened != null -> "В плейлисте нет треков"
                            tab == LibTab.LIKED -> "Вставь ссылку на свой профиль во вкладке «Плейлист» и нажми «Перенести в „Понравилось“». Или лайкай треки в Волне."
                            else -> "Скачанных треков пока нет. Скачать можно треки, где автор включил скачивание (значок ↓), или добавь свои файлы."
                        }
                    )
                }
            }
            itemsIndexed(playable, key = { i, t -> "$i:${t.key}" }) { i, t ->
                TrackRow(
                    track = t,
                    active = ui.track?.key == t.key || (t.scId != null && ui.track?.scId == t.scId),
                    onClick = { playList(playable, i) },
                    trailing = {
                        if (opened == null && tab == LibTab.DOWNLOADED) {
                            TrackMenu(t)
                        } else {
                            DownloadBadge(t, t.scId != null && t.scId in downloadedIds, t.scId?.let { states[it] })
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlaylistMenu(id: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "Ещё") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Удалить плейлист") },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { Library.removePlaylist(id); open = false },
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
