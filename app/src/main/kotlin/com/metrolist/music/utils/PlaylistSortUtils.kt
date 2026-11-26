package com.metrolist.music.utils

import com.metrolist.innertube.models.SongItem
import com.metrolist.music.constants.OnlinePlaylistSortType
import com.metrolist.music.constants.PlaylistSongSortType
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.extensions.reversed
import java.text.Collator
import java.util.Locale

private enum class SongSortField {
    ORIGINAL,
    CREATE_DATE,
    NAME,
    ARTIST,
    ALBUM,
    LENGTH,
    PLAY_TIME,
}

private data class SortMetadata(
    val title: String,
    val artists: String,
    val album: String,
    val duration: Int,
    val dateOrder: Long,
    val playTime: Long,
)

private fun <T> sortEntries(
    items: List<T>,
    field: SongSortField,
    descending: Boolean,
    locale: Locale = Locale.getDefault(),
    metadataOf: (index: Int, item: T) -> SortMetadata,
): List<T> {
    if (field == SongSortField.ORIGINAL || items.size <= 1) {
        return items
    }

    val indexed = items.mapIndexed { index, item -> item to metadataOf(index, item) }
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }

    val sorted =
        when (field) {
            SongSortField.ORIGINAL -> indexed
            SongSortField.CREATE_DATE -> indexed.sortedBy { it.second.dateOrder }
            SongSortField.NAME -> indexed.sortedWith(compareBy(collator) { it.second.title })
            SongSortField.ARTIST ->
                indexed.sortedWith(
                    compareBy<Pair<T, SortMetadata>> { it.second.artists.isBlank() }
                        .thenBy(collator) { it.second.artists }
                        .thenBy(collator) { it.second.album }
                        .thenBy(collator) { it.second.title },
                )
            SongSortField.ALBUM ->
                indexed.sortedWith(
                    compareBy<Pair<T, SortMetadata>> { it.second.album.isBlank() }
                        .thenBy(collator) { it.second.album }
                        .thenBy(collator) { it.second.title },
                )
            SongSortField.LENGTH ->
                indexed.sortedWith(
                    compareBy<Pair<T, SortMetadata>> { it.second.duration < 0 }
                        .thenBy { it.second.duration }
                        .thenBy(collator) { it.second.title },
                )
            SongSortField.PLAY_TIME ->
                indexed.sortedWith(
                    compareBy<Pair<T, SortMetadata>> { it.second.playTime }
                        .thenBy(collator) { it.second.title },
                )
        }

    return sorted.map { it.first }.reversed(descending)
}

/** Sorts saved local playlist songs according to the active user preference. */
fun sortPlaylistSongs(
    songs: List<PlaylistSong>,
    sortType: PlaylistSongSortType,
    descending: Boolean,
    locale: Locale = Locale.getDefault(),
): List<PlaylistSong> {
    val field =
        when (sortType) {
            PlaylistSongSortType.CUSTOM -> SongSortField.ORIGINAL
            PlaylistSongSortType.CREATE_DATE -> SongSortField.CREATE_DATE
            PlaylistSongSortType.NAME -> SongSortField.NAME
            PlaylistSongSortType.ARTIST -> SongSortField.ARTIST
            PlaylistSongSortType.ALBUM -> SongSortField.ALBUM
            PlaylistSongSortType.LENGTH -> SongSortField.LENGTH
            PlaylistSongSortType.PLAY_TIME -> SongSortField.PLAY_TIME
        }
    return sortEntries(songs, field, descending, locale) { _, song ->
        SortMetadata(
            title = song.song.song.title,
            artists = song.song.artists.joinToString("") { it.name },
            album = song.song.album?.title.orEmpty(),
            duration = song.song.song.duration,
            dateOrder = song.map.id.toLong(),
            playTime = song.song.song.totalPlayTime,
        )
    }
}

/** Sorts online playlist items using their loaded playlist position as date-added order. */
fun sortOnlinePlaylistSongs(
    songs: List<SongItem>,
    sortType: OnlinePlaylistSortType,
    descending: Boolean,
    locale: Locale = Locale.getDefault(),
): List<SongItem> {
    val field =
        when (sortType) {
            OnlinePlaylistSortType.DEFAULT -> SongSortField.ORIGINAL
            OnlinePlaylistSortType.CREATE_DATE -> SongSortField.CREATE_DATE
            OnlinePlaylistSortType.NAME -> SongSortField.NAME
            OnlinePlaylistSortType.ARTIST -> SongSortField.ARTIST
            OnlinePlaylistSortType.ALBUM -> SongSortField.ALBUM
            OnlinePlaylistSortType.LENGTH -> SongSortField.LENGTH
        }
    return sortEntries(songs, field, descending, locale) { index, song ->
        SortMetadata(
            title = song.title,
            artists = song.artists.joinToString("") { it.name },
            album = song.album?.name.orEmpty(),
            duration = song.duration ?: -1,
            dateOrder = index.toLong(),
            playTime = 0L,
        )
    }
}
