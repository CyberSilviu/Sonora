package com.metrolist.innertube.pages

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ContinuationItemRenderer
import com.metrolist.innertube.models.MusicResponsiveListItemRenderer
import com.metrolist.innertube.models.Run
import com.metrolist.innertube.models.Runs
import com.metrolist.innertube.models.Thumbnail
import com.metrolist.innertube.models.ThumbnailRenderer
import com.metrolist.innertube.models.Thumbnails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistPageTest {
    @Test
    fun `legacy detail subtitle has an owner in its second section`() {
        assertEquals(
            Artist("Owner & Friends", null),
            PlaylistPage.ownerFromDetailSubtitle(
                listOf(
                    Run("Playlist", null),
                    Run(" • ", null),
                    Run("Owner & Friends", null),
                    Run(" • ", null),
                    Run("14 songs", null),
                ),
            ),
        )
        assertNull(PlaylistPage.ownerFromDetailSubtitle(listOf(Run("canción", null))))
        assertNull(PlaylistPage.ownerFromDetailSubtitle(listOf(Run("Playlist", null), Run(" • ", null), Run(" ", null))))
    }

    @Test
    fun `fromMusicResponsiveListItemRenderer keeps songs without playlistSetVideoId`() {
        val renderer =
            MusicResponsiveListItemRenderer(
                badges = null,
                fixedColumns = null,
                flexColumns =
                    listOf(
                        MusicResponsiveListItemRenderer.FlexColumn(
                            MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(
                                Runs(listOf(Run("Track 101", null))),
                            ),
                        ),
                        MusicResponsiveListItemRenderer.FlexColumn(
                            MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(
                                Runs(listOf(Run("Artist Name", null))),
                            ),
                        ),
                    ),
                thumbnail =
                    ThumbnailRenderer(
                        musicThumbnailRenderer =
                            ThumbnailRenderer.MusicThumbnailRenderer(
                                thumbnail = Thumbnails(listOf(Thumbnail("https://example.com/thumb.jpg", 120, 120))),
                                thumbnailCrop = null,
                                thumbnailScale = null,
                            ),
                        musicAnimatedThumbnailRenderer = null,
                        croppedSquareThumbnailRenderer = null,
                    ),
                menu = null,
                playlistItemData =
                    MusicResponsiveListItemRenderer.PlaylistItemData(
                        playlistSetVideoId = null,
                        videoId = "video_101",
                    ),
                overlay = null,
                navigationEndpoint = null,
            )

        val song = PlaylistPage.fromMusicResponsiveListItemRenderer(renderer)
        assertNotNull(song)
        assertEquals("video_101", song?.id)
        assertNull(song?.setVideoId)
    }

    @Test
    fun `ContinuationItemRenderer extracts token from direct and executor commands`() {
        val direct =
            ContinuationItemRenderer(
                continuationEndpoint =
                    ContinuationItemRenderer.ContinuationEndpoint(
                        continuationCommand = ContinuationItemRenderer.ContinuationEndpoint.ContinuationCommand("token_direct"),
                    ),
            )
        val nested =
            ContinuationItemRenderer(
                continuationEndpoint =
                    ContinuationItemRenderer.ContinuationEndpoint(
                        continuationCommand = null,
                        commandExecutorCommand =
                            ContinuationItemRenderer.ContinuationEndpoint.CommandExecutorCommand(
                                commands =
                                    listOf(
                                        ContinuationItemRenderer.ContinuationEndpoint.CommandExecutorCommand.Command(
                                            continuationCommand = null,
                                        ),
                                        ContinuationItemRenderer.ContinuationEndpoint.CommandExecutorCommand.Command(
                                            continuationCommand =
                                                ContinuationItemRenderer.ContinuationEndpoint.ContinuationCommand("token_nested"),
                                        ),
                                    ),
                            ),
                    ),
            )

        assertEquals("token_direct", direct.getContinuation())
        assertEquals("token_nested", nested.getContinuation())
    }
}
