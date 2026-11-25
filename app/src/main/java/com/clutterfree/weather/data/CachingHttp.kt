package com.clutterfree.weather.data

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Keeps weather responses that are still inside Cache-Control or Expires, and
 * revalidates the rest with If-None-Match and If-Modified-Since.
 */
class CachingHttp(
    private val directory: File,
    private val origin: TextSource,
    private val now: () -> Instant = Instant::now,
) : TextSource {
    override fun get(url: String, headers: Map<String, String>): TextHttp.Response {
        val cached = read(url)
        val current = now()
        if (cached != null && cached.freshUntil?.isAfter(current) == true) {
            return TextHttp.Response(200, cached.body)
        }
        val conditional = buildMap {
            putAll(headers)
            cached?.etag?.let { put("If-None-Match", it) }
            cached?.lastModified?.let { put("If-Modified-Since", it) }
        }
        val response = origin.get(url, conditional)
        if (response.code == NOT_MODIFIED && cached != null) {
            if (storePolicy(response.header("cache-control")) == StorePolicy.DISCARD) {
                discard(url)
            } else {
                write(cached.copy(freshUntil = freshUntil(response.headers, current)))
            }
            return TextHttp.Response(200, cached.body, response.headers)
        }
        if (storePolicy(response.header("cache-control")) == StorePolicy.DISCARD) {
            discard(url)
        } else if (response.code in 200..299) {
            write(
                Entry(
                    url = url,
                    body = response.body,
                    etag = response.header("etag"),
                    lastModified = response.header("last-modified"),
                    freshUntil = freshUntil(response.headers, current),
                ),
            )
        }
        return response
    }

    private fun read(url: String): Entry? {
        val file = File(directory, fileName(url))
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            if (json.optString("url") != url) return null
            Entry(
                url = url,
                body = json.getString("body"),
                etag = json.optString("etag").ifBlank { null },
                lastModified = json.optString("lastModified").ifBlank { null },
                freshUntil = json.optString("freshUntil").ifBlank { null }?.let(Instant::parse),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun discard(url: String) {
        File(directory, fileName(url)).delete()
    }

    private fun write(entry: Entry) {
        directory.mkdirs()
        val json = JSONObject()
            .put("url", entry.url)
            .put("body", entry.body)
            .put("etag", entry.etag ?: "")
            .put("lastModified", entry.lastModified ?: "")
            .put("freshUntil", entry.freshUntil?.toString() ?: "")
        File(directory, fileName(entry.url)).writeText(json.toString())
    }

    private data class Entry(
        val url: String,
        val body: String,
        val etag: String?,
        val lastModified: String?,
        val freshUntil: Instant?,
    )

    private companion object {
        const val NOT_MODIFIED = 304

        fun fileName(url: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

internal enum class StorePolicy {
    KEEP,
    DISCARD,
}

internal fun storePolicy(cacheControl: String?): StorePolicy =
    if (cacheControl?.contains("no-store", ignoreCase = true) == true) StorePolicy.DISCARD else StorePolicy.KEEP

/** When the stored body can be reused without a network call. Null means revalidate. */
internal fun freshUntil(headers: Map<String, String>, receivedAt: Instant): Instant? {
    val cacheControl = headers.header("cache-control") ?: ""
    if (cacheControl.contains("no-store", ignoreCase = true)) return null
    if (cacheControl.contains("no-cache", ignoreCase = true)) return null
    val maxAge = MAX_AGE.find(cacheControl)?.groupValues?.get(1)?.toLongOrNull()
    if (maxAge != null) return receivedAt.plusSeconds(maxAge)
    val expires = headers.header("expires")?.let(::httpDate) ?: return null
    return expires.takeIf { it.isAfter(receivedAt) }
}

private fun TextHttp.Response.header(name: String): String? = headers.header(name)

private fun Map<String, String>.header(name: String): String? =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.ifBlank { null }

private fun httpDate(value: String): Instant? = try {
    Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US).withZone(ZoneOffset.UTC).parse(value))
} catch (_: Exception) {
    null
}

private val MAX_AGE = Regex("(?<![\\w-])max-age=(\\d+)")
