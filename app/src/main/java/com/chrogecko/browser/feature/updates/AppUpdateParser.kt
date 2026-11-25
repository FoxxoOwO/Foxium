package com.chrogecko.browser.feature.updates

import java.net.URI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GitHubReleaseAsset(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @SerialName("size") val size: Long = 0L,
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("assets") val assets: List<GitHubReleaseAsset> = emptyList(),
)

/** Parsed update candidate from GitHub Releases ready to display and download. */
data class AvailableUpdate(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val isPrerelease: Boolean,
    val apkUrl: String,
    val apkName: String,
    val apkSizeBytes: Long,
    val releaseUrl: String,
)

/** Pure-JVM GitHub Releases parser, semantic version comparator, and ABI APK selector. */
object AppUpdateParser {

    private val json = Json { ignoreUnknownKeys = true }

    private val trustedHosts = setOf(
        "github.com",
        "api.github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    fun normalizeVersion(raw: String): String =
        raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .trim()

    fun parseVersionParts(raw: String): List<Int> {
        val normalized = normalizeVersion(raw)
        if (normalized.isEmpty()) return emptyList()
        val parts = normalized.split('.')
        val parsed = ArrayList<Int>(parts.size)
        for (segment in parts) {
            if (segment.isEmpty()) return emptyList()
            val number = segment.toIntOrNull() ?: return emptyList()
            if (number < 0) return emptyList()
            parsed += number
        }
        return parsed
    }

    fun isNewerVersion(candidate: String, current: String): Boolean =
        compareVersions(candidate, current) > 0

    internal fun compareVersions(a: String, b: String): Int {
        val partsA = parseVersionParts(a)
        val partsB = parseVersionParts(b)
        if (partsA.isEmpty() || partsB.isEmpty()) return 0
        val count = maxOf(partsA.size, partsB.size)
        for (i in 0 until count) {
            val valA = partsA.getOrElse(i) { 0 }
            val valB = partsB.getOrElse(i) { 0 }
            if (valA != valB) return valA.compareTo(valB)
        }
        return 0
    }

    fun isTrustedHttpsUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        return host in trustedHosts || host.endsWith(".githubusercontent.com")
    }

    fun selectBestApkAsset(
        assets: List<GitHubReleaseAsset>,
        supportedAbis: List<String>,
    ): GitHubReleaseAsset? {
        val apks = assets.filter {
            it.name.endsWith(".apk", ignoreCase = true) && isTrustedHttpsUrl(it.browserDownloadUrl)
        }
        if (apks.isEmpty()) return null
        for (abi in supportedAbis) {
            val needle = abi.trim().lowercase()
            if (needle.isEmpty()) continue
            val matched = apks.firstOrNull { it.name.lowercase().contains(needle) }
            if (matched != null) return matched
        }
        return apks.firstOrNull { it.name.lowercase().contains("universal") } ?: apks.first()
    }

    fun parseLatestUpdate(
        jsonBody: String,
        currentVersion: String,
        includePrereleases: Boolean,
        supportedAbis: List<String>,
    ): AvailableUpdate? {
        val releases = runCatching { json.decodeFromString<List<GitHubRelease>>(jsonBody) }.getOrNull()
            ?: return null
        var best: AvailableUpdate? = null
        for (release in releases) {
            if (release.draft) continue
            if (release.prerelease && !includePrereleases) continue
            if (!isNewerVersion(release.tagName, currentVersion)) continue
            val apk = selectBestApkAsset(release.assets, supportedAbis) ?: continue
            val candidate = AvailableUpdate(
                tagName = release.tagName,
                versionName = normalizeVersion(release.tagName),
                title = release.name.ifBlank { release.tagName },
                releaseNotes = release.body.trim(),
                isPrerelease = release.prerelease,
                apkUrl = apk.browserDownloadUrl,
                apkName = apk.name,
                apkSizeBytes = apk.size,
                releaseUrl = release.htmlUrl,
            )
            val order = best?.let { compareVersions(candidate.versionName, it.versionName) } ?: 1
            // `versionName` drops the prerelease suffix, so v1.2.3-rc1 ties with v1.2.3.
            // Prefer the final release in that case instead of whichever GitHub listed first.
            if (order > 0 || (order == 0 && best?.isPrerelease == true && !candidate.isPrerelease)) {
                best = candidate
            }
        }
        return best
    }
}
