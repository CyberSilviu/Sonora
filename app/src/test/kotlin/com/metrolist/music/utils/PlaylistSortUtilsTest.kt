package com.metrolist.music.utils

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.constants.OnlinePlaylistSortType
import com.metrolist.music.constants.PlaylistSongSortType
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.AlbumEntity
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PlaylistSortUtilsTest {
    private fun onlineSong(
        id: String,
        title: String,
        artist: String,
        album: String? = null,
        duration: Int? = null,
    ) = SongItem(
        id = id,
        title = title,
        artists = if (artist.isBlank()) emptyList() else listOf(Artist(artist, null)),
        album = album?.let { Album(it, "album_$it") },
        duration = duration,
        thumbnail = "",
    )

    private fun localSong(
        mapId: Int,
        position: Int,
        title: String,
        artist: String,
        album: String? = null,
        duration: Int = 120,
        playTime: Long = 0L,
    ) = PlaylistSong(
        map = PlaylistSongMap(id = mapId, playlistId = "pl", songId = "s$mapId", position = position),
        song =
            Song(
                song =
                    SongEntity(
                        id = "s$mapId",
                        title = title,
                        duration = duration,
                        totalPlayTime = playTime,
                    ),
                artists = if (artist.isBlank()) emptyList() else listOf(ArtistEntity("a$mapId", artist)),
                album = album?.let { AlbumEntity(id = "alb$mapId", title = it, songCount = 1, duration = duration) },
            ),
    )

    @Test
    fun sortOnlinePlaylistSongs_supportsAllModes() {
        val input =
            listOf(
                onlineSong("1", "Zebra", "Beta", album = "Middle", duration = 240),
                onlineSong("2", "Alpha", "Alpha", album = null, duration = 120),
                onlineSong("3", "Middle", "Gamma", album = "First", duration = 180),
            )

        assertEquals(
            listOf("1", "2", "3"),
            sortOnlinePlaylistSongs(input, OnlinePlaylistSortType.DEFAULT, descending = true, Locale.US).map { it.id },
        )
        assertEquals(
            listOf("1", "2", "3"),
            sortOnlinePlaylistSongs(input, OnlinePlaylistSortType.CREATE_DATE, descending = false, Locale.US).map { it.id },
        )
        assertEquals(
            listOf("3", "2", "1"),
            sortOnlinePlaylistSongs(input, OnlinePlaylistSortType.CREATE_DATE, descending = true, Locale.US).map { it.id },
        )
        assertEquals(
            listOf("2", "1", "3"),
            sortOnlinePlaylistSongs(input, OnlinePlaylistSortType.ARTIST, descending = false, Locale.US).map { it.id },
        )
        assertEquals(
            listOf("2", "3", "1"),
            sortOnlinePlaylistSongs(input, OnlinePlaylistSortType.NAME, descending = false, Locale.US).map { it.id },
        )
        assertEquals(
            listOf("3", "1", "2"),
            sortOnlinePlaylistSongs(input, OnlinePlaylistSortType.ALBUM, descending = false, Locale.US).map { it.id },
        )
        assertEquals(
            listOf("1", "3", "2"),
            sortOnlinePlaylistSongs(input, OnlinePlaylistSortType.LENGTH, descending = true, Locale.US).map { it.id },
        )
    }

    @Test
    fun sortPlaylistSongs_supportsArtistDateAddedAlbumLengthAndPlayTime() {
        val input =
            listOf(
                localSong(mapId = 30, position = 0, title = "Track C", artist = "Zeta", album = "Beta", duration = 300, playTime = 500),
                localSong(mapId = 10, position = 1, title = "Track A", artist = "Alpha", album = "Alpha", duration = 150, playTime = 100),
                localSong(mapId = 20, position = 2, title = "Track B", artist = "Mike", album = null, duration = 200, playTime = 900),
            )

        assertEquals(
            listOf(30, 10, 20),
            sortPlaylistSongs(input, PlaylistSongSortType.CUSTOM, descending = true, Locale.US).map { it.map.id },
        )
        assertEquals(
            listOf(30, 20, 10),
            sortPlaylistSongs(input, PlaylistSongSortType.CREATE_DATE, descending = true, Locale.US).map { it.map.id },
        )
        assertEquals(
            listOf(10, 20, 30),
            sortPlaylistSongs(input, PlaylistSongSortType.ARTIST, descending = false, Locale.US).map { it.map.id },
        )
        assertEquals(
            listOf(10, 30, 20),
            sortPlaylistSongs(input, PlaylistSongSortType.ALBUM, descending = false, Locale.US).map { it.map.id },
        )
        assertEquals(
            listOf(30, 20, 10),
            sortPlaylistSongs(input, PlaylistSongSortType.LENGTH, descending = true, Locale.US).map { it.map.id },
        )
        assertEquals(
            listOf(20, 30, 10),
            sortPlaylistSongs(input, PlaylistSongSortType.PLAY_TIME, descending = true, Locale.US).map { it.map.id },
        )
    }
}
