/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.OnlinePlaylistSortDescendingKey
import com.metrolist.music.constants.OnlinePlaylistSortType
import com.metrolist.music.constants.OnlinePlaylistSortTypeKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import com.metrolist.music.utils.sortOnlinePlaylistSongs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.metrolist.music.constants.SongSortType
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.Album
import javax.inject.Inject

@HiltViewModel
class OnlinePlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val database: MusicDatabase
) : ViewModel() {
    private val playlistId = savedStateHandle.get<String>("playlistId")!!

    // Check if this is a special podcast playlist (with or without VL prefix)
    private val normalizedPlaylistId = playlistId.removePrefix("VL")
    val isPodcastPlaylist = normalizedPlaylistId == "RDPN" || normalizedPlaylistId == "SE"

    val playlist = MutableStateFlow<PlaylistItem?>(null)
    private val _rawPlaylistSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val rawPlaylistSongs = _rawPlaylistSongs.asStateFlow()
    val playlistSongs: StateFlow<List<SongItem>> =
        combine(
            _rawPlaylistSongs,
            context.dataStore.data
                .map {
                    (it[OnlinePlaylistSortTypeKey].toEnum(OnlinePlaylistSortType.DEFAULT)) to
                        (it[OnlinePlaylistSortDescendingKey] ?: true)
                }.distinctUntilChanged(),
        ) { songs, (sortType, sortDescending) ->
            sortOnlinePlaylistSongs(songs, sortType, sortDescending)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _loadMoreFailed = MutableStateFlow(false)
    val loadMoreFailed = _loadMoreFailed.asStateFlow()

    val dbPlaylist = database.playlistByBrowseId(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _continuation = MutableStateFlow<String?>(null)
    val continuation = _continuation.asStateFlow()

    private var proactiveLoadJob: Job? = null

    init {
        fetchInitialPlaylistData()
    }

    private fun fetchInitialPlaylistData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            _continuation.value = null
            _loadMoreFailed.value = false
            proactiveLoadJob?.cancel() // Cancel any ongoing proactive load

            if (isPodcastPlaylist) {
                // Use special podcast playlist APIs
                fetchPodcastPlaylist()
            } else {
                // Use regular playlist API
                fetchRegularPlaylist()
            }
        }
    }

    private suspend fun fetchPodcastPlaylist() {
        when (normalizedPlaylistId) {
            "RDPN" -> {
                YouTube.newEpisodes()
                    .onSuccess { episodes ->
                        playlist.value = PlaylistItem(
                            id = playlistId,
                            title = "New Episodes",
                            author = null,
                            songCountText = "${episodes.size} episodes",
                            thumbnail = episodes.firstOrNull()?.thumbnail ?: "",
                            playEndpoint = null,
                            shuffleEndpoint = null,
                            radioEndpoint = null,
                        )
                        _rawPlaylistSongs.value = applySongFilters(episodes)
                        _isLoading.value = false
                    }.onFailure { throwable ->
                        _error.value = throwable.message ?: "Failed to load new episodes"
                        _isLoading.value = false
                        reportException(throwable)
                    }
            }
            "SE" -> {
                timber.log.Timber.d("[SE_LOCAL] Fetching SE playlist...")
                val result = YouTube.episodesForLater()
                val episodes = result.getOrNull() ?: emptyList()
                timber.log.Timber.d("[SE_LOCAL] YouTube API result: ${if (result.isSuccess) "success" else "failed"}, ${episodes.size} episodes")

                if (result.isSuccess && episodes.isNotEmpty()) {
                    // Use YouTube episodes
                    playlist.value = PlaylistItem(
                        id = playlistId,
                        title = "Episodes for Later",
                        author = null,
                        songCountText = "${episodes.size} episodes",
                        thumbnail = episodes.firstOrNull()?.thumbnail ?: "",
                        playEndpoint = null,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                    )
                    _rawPlaylistSongs.value = applySongFilters(episodes)
                    _isLoading.value = false
                } else {
                    // Fall back to local saved episodes when API fails or returns empty
                    timber.log.Timber.d("[SE_LOCAL] Falling back to local saved episodes")
                    loadLocalSavedEpisodes()
                }
            }
            else -> {
                _error.value = "Unknown podcast playlist"
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchRegularPlaylist() {
        YouTube.playlist(playlistId)
            .onSuccess { playlistPage ->
                val initialContinuation = playlistPage.songsContinuation ?: playlistPage.continuation
                playlist.value = playlistPage.playlist
                _rawPlaylistSongs.value = applySongFilters(playlistPage.songs)
                _continuation.value = initialContinuation
                _isLoading.value = false
                if (initialContinuation != null) {
                    startProactiveBackgroundLoading()
                }
            }.onFailure { throwable ->
                _error.value = throwable.message ?: "Failed to load playlist"
                _isLoading.value = false
                reportException(throwable)
            }
    }

    private suspend fun loadLocalSavedEpisodes() {
        timber.log.Timber.d("[SE_LOCAL] loadLocalSavedEpisodes called")
        val savedEpisodes = database.savedPodcastEpisodes(SongSortType.CREATE_DATE, true).firstOrNull() ?: emptyList()
        timber.log.Timber.d("[SE_LOCAL] Found ${savedEpisodes.size} saved episodes")
        savedEpisodes.forEachIndexed { index, ep ->
            timber.log.Timber.d("[SE_LOCAL] Episode $index: id=${ep.song.id}, title=${ep.song.title}, isEpisode=${ep.song.isEpisode}, inLibrary=${ep.song.inLibrary}")
        }
        if (savedEpisodes.isNotEmpty()) {
            // Convert local Song entities to SongItem format
            val songItems = savedEpisodes.map { song ->
                SongItem(
                    id = song.song.id,
                    title = song.song.title,
                    artists = song.artists.map { Artist(it.id, it.name) },
                    album = song.album?.let { com.metrolist.innertube.models.Album(it.id, it.title) },
                    duration = song.song.duration,
                    thumbnail = song.song.thumbnailUrl ?: "",
                    explicit = song.song.explicit,
                    endpoint = null,
                )
            }
            timber.log.Timber.d("[SE_LOCAL] Converted to ${songItems.size} SongItems")
            playlist.value = PlaylistItem(
                id = playlistId,
                title = "Episodes for Later",
                author = null,
                songCountText = "${songItems.size} episodes",
                thumbnail = songItems.firstOrNull()?.thumbnail ?: "",
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
            )
            val filtered = applySongFilters(songItems)
            timber.log.Timber.d("[SE_LOCAL] After filter: ${filtered.size} episodes, setting playlistSongs")
            _rawPlaylistSongs.value = filtered
            _isLoading.value = false
            timber.log.Timber.d("[SE_LOCAL] Done, isLoading=false")
        } else {
            timber.log.Timber.d("[SE_LOCAL] No saved episodes found")
            _error.value = "No saved episodes"
            _isLoading.value = false
        }
    }

    private fun startProactiveBackgroundLoading() {
        if (proactiveLoadJob?.isActive == true) return
        _loadMoreFailed.value = false
        proactiveLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val seenTokens = mutableSetOf<String>()
            var failures = 0
            while (isActive) {
                val token = _continuation.value ?: break
                YouTube.playlistContinuation(token)
                    .onSuccess { playlistContinuationPage ->
                        failures = 0
                        seenTokens += token
                        _rawPlaylistSongs.value = applySongFilters(_rawPlaylistSongs.value + playlistContinuationPage.songs)
                        _continuation.value = playlistContinuationPage.continuation?.takeIf { it !in seenTokens }
                    }.onFailure { throwable ->
                        ensureActive()
                        reportException(throwable)
                        // Transient failures (including InnerTube's "session changed" cancellation) used to
                        // end loading for good. Retry a few times, then keep the token so the user can resume.
                        if (++failures >= MAX_PAGE_ATTEMPTS) {
                            _loadMoreFailed.value = true
                            return@launch
                        }
                        delay(PAGE_RETRY_DELAY_MS * failures)
                    }
            }
        }
    }

    fun loadMoreSongs() = startProactiveBackgroundLoading()

    fun retry() {
        proactiveLoadJob?.cancel()
        fetchInitialPlaylistData() // This will also restart proactive loading if applicable
    }

    private fun applySongFilters(songs: List<SongItem>): List<SongItem> {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        return songs
            .distinctBy { it.id }
            .filterVideoSongs(hideVideoSongs)
    }

    override fun onCleared() {
        super.onCleared()
        proactiveLoadJob?.cancel()
    }

    private companion object {
        const val MAX_PAGE_ATTEMPTS = 3
        const val PAGE_RETRY_DELAY_MS = 1000L
    }
}
