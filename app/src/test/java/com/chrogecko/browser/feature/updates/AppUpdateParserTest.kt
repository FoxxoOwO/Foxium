package com.chrogecko.browser.feature.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateParserTest {

    @Test
    fun `isNewerVersion compares semantic parts accurately`() {
        assertTrue(AppUpdateParser.isNewerVersion("v0.76.2", "0.76.1"))
        assertTrue(AppUpdateParser.isNewerVersion("v0.77.0-experimental", "0.76.1-debug"))
        assertTrue(AppUpdateParser.isNewerVersion("1.0.0", "0.99.9"))
        assertTrue(AppUpdateParser.isNewerVersion("0.76.1.1", "0.76.1"))

        assertFalse(AppUpdateParser.isNewerVersion("v0.76.1-experimental", "0.76.1"))
        assertFalse(AppUpdateParser.isNewerVersion("0.76.0", "0.76.1"))
        assertFalse(AppUpdateParser.isNewerVersion("invalid-tag", "0.76.1"))
        assertFalse(AppUpdateParser.isNewerVersion("", "0.76.1"))
    }

    @Test
    fun `final release is newer than its prerelease`() {
        assertTrue(AppUpdateParser.isNewerVersion("1.0.0", "1.0.0-rc1"))
        assertTrue(AppUpdateParser.isNewerVersion("1.0.0-rc2", "1.0.0-rc1"))
        assertFalse(AppUpdateParser.isNewerVersion("1.0.0-rc1", "1.0.0"))
    }

    @Test
    fun `parseLatestUpdate skips drafts and respects prerelease filter`() {
        val json = """
            [
              {
                "tag_name": "v0.78.0",
                "name": "Draft Release",
                "body": "Work in progress",
                "prerelease": false,
                "draft": true,
                "html_url": "https://github.com/FoxxoOwO/Foxium/releases/tag/v0.78.0",
                "assets": [
                  {
                    "name": "app-arm64-v8a-release.apk",
                    "browser_download_url": "https://github.com/FoxxoOwO/Foxium/releases/download/v0.78.0/app-arm64-v8a-release.apk",
                    "size": 1000
                  }
                ]
              },
              {
                "tag_name": "v0.77.0-experimental",
                "name": "Foxium 0.77.0 Experimental",
                "body": "Experimental notes",
                "prerelease": true,
                "draft": false,
                "html_url": "https://github.com/FoxxoOwO/Foxium/releases/tag/v0.77.0-experimental",
                "assets": [
                  {
                    "name": "app-arm64-v8a-release.apk",
                    "browser_download_url": "https://github.com/FoxxoOwO/Foxium/releases/download/v0.77.0-experimental/app-arm64-v8a-release.apk",
                    "size": 2000
                  }
                ]
              },
              {
                "tag_name": "v0.76.2",
                "name": "Foxium 0.76.2",
                "body": "Stable notes",
                "prerelease": false,
                "draft": false,
                "html_url": "https://github.com/FoxxoOwO/Foxium/releases/tag/v0.76.2",
                "assets": [
                  {
                    "name": "app-arm64-v8a-release.apk",
                    "browser_download_url": "https://github.com/FoxxoOwO/Foxium/releases/download/v0.76.2/app-arm64-v8a-release.apk",
                    "size": 1500
                  }
                ]
              }
            ]
        """.trimIndent()

        val stableOnly = AppUpdateParser.parseLatestUpdate(
            jsonBody = json,
            currentVersion = "0.76.1",
            includePrereleases = false,
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        )
        assertNotNull(stableOnly)
        assertEquals("v0.76.2", stableOnly?.tagName)
        assertFalse(stableOnly!!.isPrerelease)

        val withExperimental = AppUpdateParser.parseLatestUpdate(
            jsonBody = json,
            currentVersion = "0.76.1",
            includePrereleases = true,
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        )
        assertNotNull(withExperimental)
        assertEquals("v0.77.0-experimental", withExperimental?.tagName)
        assertEquals("0.77.0", withExperimental?.versionName)
        assertTrue(withExperimental!!.isPrerelease)
    }

    @Test
    fun `parseLatestUpdate prefers stable release over prerelease with same numeric version`() {
        val json = """
            [
              {
                "tag_name": "v1.0.0-rc1",
                "name": "1.0.0 RC1",
                "body": "",
                "prerelease": true,
                "draft": false,
                "html_url": "https://github.com/FoxxoOwO/Foxium/releases/tag/v1.0.0-rc1",
                "assets": [
                  {
                    "name": "app-arm64-v8a-release.apk",
                    "browser_download_url": "https://github.com/FoxxoOwO/Foxium/releases/download/v1.0.0-rc1/app-arm64-v8a-release.apk",
                    "size": 1000
                  }
                ]
              },
              {
                "tag_name": "v1.0.0",
                "name": "1.0.0",
                "body": "",
                "prerelease": false,
                "draft": false,
                "html_url": "https://github.com/FoxxoOwO/Foxium/releases/tag/v1.0.0",
                "assets": [
                  {
                    "name": "app-arm64-v8a-release.apk",
                    "browser_download_url": "https://github.com/FoxxoOwO/Foxium/releases/download/v1.0.0/app-arm64-v8a-release.apk",
                    "size": 1000
                  }
                ]
              }
            ]
        """.trimIndent()
        val update = AppUpdateParser.parseLatestUpdate(
            jsonBody = json,
            currentVersion = "0.9.0",
            includePrereleases = true,
            supportedAbis = listOf("arm64-v8a"),
        )
        assertNotNull(update)
        assertEquals("v1.0.0", update?.tagName)
        assertFalse(update!!.isPrerelease)
    }

    @Test
    fun `parseLatestUpdate picks best ABI or universal fallback`() {
        val json = """
            [
              {
                "tag_name": "v0.77.0",
                "name": "Release 0.77.0",
                "body": "",
                "prerelease": false,
                "draft": false,
                "html_url": "https://github.com/FoxxoOwO/Foxium/releases/tag/v0.77.0",
                "assets": [
                  {
                    "name": "app-armeabi-v7a-release.apk",
                    "browser_download_url": "https://github.com/FoxxoOwO/Foxium/releases/download/v0.77.0/app-armeabi-v7a-release.apk",
                    "size": 1100
                  },
                  {
                    "name": "app-universal-release.apk",
                    "browser_download_url": "https://objects.githubusercontent.com/foxium/app-universal-release.apk",
                    "size": 1900
                  },
                  {
                    "name": "app-arm64-v8a-release.apk",
                    "browser_download_url": "https://github.com/FoxxoOwO/Foxium/releases/download/v0.77.0/app-arm64-v8a-release.apk",
                    "size": 1200
                  }
                ]
              }
            ]
        """.trimIndent()

        val arm64 = AppUpdateParser.parseLatestUpdate(
            jsonBody = json,
            currentVersion = "0.76.1",
            includePrereleases = true,
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        )
        assertEquals("app-arm64-v8a-release.apk", arm64?.apkName)
        assertEquals(1200L, arm64?.apkSizeBytes)

        val fallbackUniversal = AppUpdateParser.parseLatestUpdate(
            jsonBody = json,
            currentVersion = "0.76.1",
            includePrereleases = true,
            supportedAbis = listOf("riscv64"),
        )
        assertEquals("app-universal-release.apk", fallbackUniversal?.apkName)
    }

    @Test
    fun `parseLatestUpdate rejects untrusted or non-https asset URLs`() {
        val json = """
            [
              {
                "tag_name": "v0.77.0",
                "name": "Suspicious",
                "body": "",
                "prerelease": false,
                "draft": false,
                "html_url": "https://github.com/FoxxoOwO/Foxium/releases/tag/v0.77.0",
                "assets": [
                  {
                    "name": "app-arm64-v8a-release.apk",
                    "browser_download_url": "http://github.com/FoxxoOwO/Foxium/releases/download/v0.77.0/app-arm64-v8a-release.apk",
                    "size": 100
                  },
                  {
                    "name": "app-universal-release.apk",
                    "browser_download_url": "https://evil.example.com/foxium.apk",
                    "size": 100
                  }
                ]
              }
            ]
        """.trimIndent()

        val result = AppUpdateParser.parseLatestUpdate(
            jsonBody = json,
            currentVersion = "0.76.1",
            includePrereleases = true,
            supportedAbis = listOf("arm64-v8a"),
        )
        assertNull(result)
    }
}
