package com.fabrice.monumentsnearby.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `compareVersions - égalité`() {
        assertEquals(0, UpdateChecker.compareVersions("0.2.0", "0.2.0"))
        assertEquals(0, UpdateChecker.compareVersions("1.0", "1.0.0"))
        assertEquals(0, UpdateChecker.compareVersions("0.3", "0.3.0"))
    }

    @Test
    fun `compareVersions - plus récent`() {
        assert(UpdateChecker.compareVersions("0.3.0", "0.2.0") > 0)
        assert(UpdateChecker.compareVersions("1.0.0", "0.9.9") > 0)
        assert(UpdateChecker.compareVersions("0.10.0", "0.9.0") > 0)
        assert(UpdateChecker.compareVersions("0.2.10", "0.2.9") > 0)
    }

    @Test
    fun `compareVersions - plus ancien`() {
        assert(UpdateChecker.compareVersions("0.2.0", "0.3.0") < 0)
        assert(UpdateChecker.compareVersions("0.9.9", "1.0.0") < 0)
    }

    @Test
    fun `parseReleases - prend la version la plus haute avec APK`() {
        val json = """
            [
              {"tag_name": "v0.2.0", "draft": false,
               "body": "fix caméra", "published_at": "2026-08-15T20:20:33Z",
               "assets": [{"name": "monuments-nearby-v0.2.0.apk", "browser_download_url": "https://x/v0.2.0.apk"}]},
              {"tag_name": "v0.3.0", "draft": false,
               "body": "mise à jour auto", "published_at": "2026-08-16T08:00:00Z",
               "assets": [{"name": "monuments-nearby-v0.3.0.apk", "browser_download_url": "https://x/v0.3.0.apk"}]},
              {"tag_name": "v0.4.0-beta", "draft": true,
               "assets": [{"name": "monuments-nearby-v0.4.0.apk", "browser_download_url": "https://x/v0.4.0.apk"}]}
            ]
        """.trimIndent()
        val info = UpdateChecker.parseReleases(json)
        assertEquals("0.3.0", info?.versionName)
        assertEquals("https://x/v0.3.0.apk", info?.downloadUrl)
        assertEquals("mise à jour auto", info?.notes)
    }

    @Test
    fun `parseReleases - ignore les drafts`() {
        val json = """
            [
              {"tag_name": "v9.9.9", "draft": true,
               "assets": [{"name": "monuments-nearby-v9.9.9.apk", "browser_download_url": "https://x/9.9.9.apk"}]}
            ]
        """.trimIndent()
        assertNull(UpdateChecker.parseReleases(json))
    }

    @Test
    fun `parseReleases - vide`() {
        assertNull(UpdateChecker.parseReleases("[]"))
        assertNull(UpdateChecker.parseReleases("pas du json"))
    }

    @Test
    fun `parseReleases - release roulante "latest" via le nom de l'APK`() {
        // La release "latest" (push sur main) n'a pas de tag versionné :
        // la version doit être extraite du nom de l'APK.
        val json = """
            [
              {"tag_name": "latest", "draft": false,
               "body": "build automatique", "published_at": "2026-08-17T10:00:00Z",
               "assets": [{"name": "monuments-nearby-v0.7.3.apk", "browser_download_url": "https://x/latest.apk"}]},
              {"tag_name": "v0.7.2", "draft": false,
               "assets": [{"name": "monuments-nearby-v0.7.2.apk", "browser_download_url": "https://x/v0.7.2.apk"}]}
            ]
        """.trimIndent()
        val info = UpdateChecker.parseReleases(json)
        assertEquals("0.7.3", info?.versionName)
        assertEquals("https://x/latest.apk", info?.downloadUrl)
    }

    @Test
    fun `parseReleases - ignore une release sans version identifiable`() {
        val json = """
            [
              {"tag_name": "latest", "draft": false,
               "assets": [{"name": "app-release.apk", "browser_download_url": "https://x/app.apk"}]}
            ]
        """.trimIndent()
        assertNull(UpdateChecker.parseReleases(json))
    }

    @Test
    fun `versionFromAssetName - extraction`() {
        assertEquals("0.7.2", UpdateChecker.versionFromAssetName("monuments-nearby-v0.7.2.apk"))
        assertEquals("1.0", UpdateChecker.versionFromAssetName("app-1.0.apk"))
        assertNull(UpdateChecker.versionFromAssetName("app-release.apk"))
    }
}
