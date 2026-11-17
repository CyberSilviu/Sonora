package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdaterTest {
    @Test
    fun parsesSonoraReleaseArtifact() {
        val response =
            """
            {
              "tag_name": "v1.1.0",
              "name": "Sonora v1.1.0",
              "body": null,
              "published_at": "2026-11-17T12:00:00Z",
              "assets": [{
                "name": "Sonora.apk",
                "browser_download_url": "https://github.com/CyberSilviu/Sonora/releases/download/v1.1.0/Sonora.apk",
                "size": 42
              }]
            }
            """.trimIndent()
        val release = Updater.parseReleaseJson(response)

        assertEquals("1.1.0", release.versionName)
        assertEquals("", release.description)
        assertEquals(
            "https://github.com/CyberSilviu/Sonora/releases/download/v1.1.0/Sonora.apk",
            release.assets.single().downloadUrl,
        )
        assertEquals("foss", release.assets.single().variant)
        assertTrue(Updater.isUpdateAvailable("1.0.0", release.versionName))
        assertFalse(Updater.isUpdateAvailable("1.1.0", release.versionName))
    }
}
