package com.monito.expense.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Looks up the newest published build on GitHub. Nothing about the user or the device is sent: it is
 * a plain read of the public "latest release" entry, and it only runs when the user asks for it.
 */
object UpdateChecker {

    const val LATEST_API = "https://api.github.com/repos/doonsMonk/Monito/releases/latest"
    const val RELEASES_PAGE = "https://github.com/doonsMonk/Monito/releases/latest"

    data class Release(val version: String, val apkUrl: String?, val pageUrl: String, val notes: String)

    /** Numeric parts of a version such as "v9.1" or "9.10.2"; anything that is not a number counts as 0. */
    private fun parts(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V").split('.', '-', '_').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    /** True when [latest] is a higher version than [current] ("9.10" is newer than "9.9"). */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Reads the GitHub "latest release" JSON; null when it does not describe a release. */
    fun parse(json: String): Release? = runCatching {
        val o = JSONObject(json)
        val tag = o.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        var apk: String? = null
        val assets = o.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = a.optString("browser_download_url").takeIf { it.startsWith("https://") }
                    break
                }
            }
        }
        Release(
            version = tag.removePrefix("v").removePrefix("V"),
            apkUrl = apk,
            pageUrl = o.optString("html_url").takeIf { it.startsWith("https://") } ?: RELEASES_PAGE,
            notes = o.optString("body").trim(),
        )
    }.getOrNull()

    /** Network call; run it off the main thread. Null means the check could not be completed. */
    fun fetchLatest(): Release? = runCatching {
        val conn = URL(LATEST_API).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Monito-Android")
            if (conn.responseCode != 200) return null
            parse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
