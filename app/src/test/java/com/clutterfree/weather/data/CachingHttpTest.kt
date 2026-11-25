package com.clutterfree.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files
import java.time.Instant

class CachingHttpTest {
    @Test
    fun freshMaxAgeSkipsTheNetwork() {
        val origin = ScriptedHttp(
            TextHttp.Response(200, "forecast", mapOf("cache-control" to "public, max-age=60", "etag" to "\"a\"")),
        )
        val clock = Clock(Instant.parse("2026-11-24T18:00:00Z"))
        val http = CachingHttp(Files.createTempDirectory("nws").toFile(), origin, clock::now)

        assertEquals("forecast", http.get("https://api.weather.gov/forecast").body)
        clock.instant = clock.instant.plusSeconds(30)
        assertEquals("forecast", http.get("https://api.weather.gov/forecast").body)
        assertEquals(1, origin.calls)
    }

    @Test
    fun expiredEntryRevalidatesWithTheEtag() {
        val origin = ScriptedHttp(
            TextHttp.Response(200, "forecast", mapOf("cache-control" to "max-age=10", "etag" to "\"a\"")),
            TextHttp.Response(304, "", mapOf("cache-control" to "max-age=10", "etag" to "\"a\"")),
        )
        val clock = Clock(Instant.parse("2026-11-24T18:00:00Z"))
        val http = CachingHttp(Files.createTempDirectory("nws").toFile(), origin, clock::now)

        http.get("https://api.weather.gov/forecast")
        clock.instant = clock.instant.plusSeconds(11)
        val again = http.get("https://api.weather.gov/forecast", mapOf("Accept" to "application/geo+json"))

        assertEquals(200, again.code)
        assertEquals("forecast", again.body)
        assertEquals("\"a\"", origin.sent[1]["If-None-Match"])
        assertEquals("application/geo+json", origin.sent[1]["Accept"])
    }

    @Test
    fun noStoreIsNotReused() {
        val origin = ScriptedHttp(
            TextHttp.Response(200, "one", mapOf("cache-control" to "no-store", "etag" to "\"a\"")),
            TextHttp.Response(200, "two", mapOf("cache-control" to "no-store")),
        )
        val http = CachingHttp(Files.createTempDirectory("nws").toFile(), origin)

        assertEquals("one", http.get("https://api.weather.gov/obs").body)
        assertEquals("two", http.get("https://api.weather.gov/obs").body)
        assertNull(origin.sent[1]["If-None-Match"])
    }

    private class Clock(var instant: Instant) {
        fun now(): Instant = instant
    }

    private class ScriptedHttp(vararg responses: TextHttp.Response) : TextSource {
        private val pending = responses.toMutableList()
        var calls = 0
        val sent = mutableListOf<Map<String, String>>()

        override fun get(url: String, headers: Map<String, String>): TextHttp.Response {
            calls += 1
            sent += headers
            return pending.removeAt(0)
        }
    }
}
