package com.metrolist.innertube.utils

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.LibraryPage
import com.metrolist.innertube.pages.PlaylistContinuationPage
import com.metrolist.innertube.pages.PlaylistPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.security.MessageDigest

private const val MAX_CONTINUATION_RETRIES = 3
private const val CONTINUATION_RETRY_DELAY_MS = 500L

@JvmName("completedLibrary")
suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> = runCatching {
    val page = getOrThrow()
    val songs = page.songs.toMutableList()
    var continuation = page.songsContinuation ?: page.continuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0

    while (continuation != null) {
        // Liked Music has no 5000-song limit, so allow far more pages than regular playlists need.
        check(requestCount++ < 500) { "Playlist pagination exceeded 500 requests" }
        check(seenContinuations.add(continuation)) { "Playlist pagination repeated a continuation" }

        val currentToken = continuation
        var lastError: Throwable? = null
        var continuationPage: PlaylistContinuationPage? = null
        for (attempt in 1..MAX_CONTINUATION_RETRIES) {
            val result = YouTube.playlistContinuation(currentToken)
            val error = result.exceptionOrNull()
            if (error is CancellationException) throw error
            if (result.isSuccess) {
                continuationPage = result.getOrThrow()
                break
            }
            lastError = error
            if (attempt < MAX_CONTINUATION_RETRIES) {
                delay(CONTINUATION_RETRY_DELAY_MS * attempt)
            }
        }
        val resolvedPage = continuationPage ?: throw (lastError ?: IllegalStateException("Failed to fetch playlist continuation"))
        songs += resolvedPage.songs
        continuation = resolvedPage.continuation
    }
    PlaylistPage(
        playlist = page.playlist,
        songs = songs,
        songsContinuation = null,
        continuation = null
    )
}

@JvmName("completedPlaylist")
suspend fun Result<LibraryPage>.completed(): Result<LibraryPage> = runCatching {
    val page = getOrThrow()
    val items = page.items.toMutableList()
    var continuation = page.continuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0

    while (continuation != null) {
        check(requestCount++ < 50) { "Library pagination exceeded 50 requests" }
        check(seenContinuations.add(continuation)) { "Library pagination repeated a continuation" }

        val continuationPage = YouTube.libraryContinuation(continuation, isUploaded = page.isUploaded).getOrThrow()
        items += continuationPage.items
        continuation = continuationPage.continuation
    }
    page.copy(items = items, continuation = null)
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun sha1(str: String): String = MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()

fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split("; ")
        .filter { it.isNotEmpty() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) null
            else part.substring(0, splitIndex) to part.substring(splitIndex + 1)
        }
        .toMap()

fun String.parseTime(): Int? {
    try {
        // YouTube Music returns duration with locale-dependent separators
        // (":" en-US, "." some locales, "," EU). Accept all.
        val parts = split(Regex("[:.,]")).map { it.toInt() }
        if (parts.size == 2) {
            return parts[0] * 60 + parts[1]
        }
        if (parts.size == 3) {
            return parts[0] * 3600 + parts[1] * 60 + parts[2]
        }
    } catch (e: Exception) {
        return null
    }
    return null
}
