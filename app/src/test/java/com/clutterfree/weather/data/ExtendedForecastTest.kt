package com.clutterfree.weather.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant

class ExtendedForecastTest {
    @Test
    fun freshCopySkipsTheNetwork() {
        val origin = ScriptedHttp(
            TextHttp.Response(200, """{"v":1,"run":"1"}""", mapOf("cache-control" to "public, max-age=3600", "etag" to "\"a\"")),
        )
        val clock = Clock(Instant.parse("2026-11-25T01:00:00Z"))
        val directory = Files.createTempDirectory("extended").toFile()
        val forecast = ExtendedForecast(directory, origin, clock::now)

        forecast.ensure()
        clock.instant = clock.instant.plusSeconds(60)
        forecast.ensure()

        assertEquals(1, origin.sent.size)
        assertTrue(File(directory, "extended.json").readText().startsWith("{\"v\":1"))
    }

    @Test
    fun expiredCopyAsksWhetherTheFileChanged() {
        val origin = ScriptedHttp(
            TextHttp.Response(200, """{"v":1,"run":"1"}""", mapOf("cache-control" to "max-age=10", "etag" to "\"a\"", "last-modified" to "Wed, 25 Nov 2026 01:00:00 GMT")),
            TextHttp.Response(304, "", mapOf("cache-control" to "max-age=10", "etag" to "\"a\"")),
        )
        val clock = Clock(Instant.parse("2026-11-25T01:00:00Z"))
        val directory = Files.createTempDirectory("extended").toFile()
        val forecast = ExtendedForecast(directory, origin, clock::now)

        forecast.ensure()
        clock.instant = clock.instant.plusSeconds(11)
        forecast.ensure()

        assertEquals("\"a\"", origin.sent[1]["If-None-Match"])
        assertEquals("Wed, 25 Nov 2026 01:00:00 GMT", origin.sent[1]["If-Modified-Since"])
        assertEquals("""{"v":1,"run":"1"}""", File(directory, "extended.json").readText())
    }

    @Test
    fun aChangedFileReplacesTheSavedCopy() {
        val origin = ScriptedHttp(
            TextHttp.Response(200, """{"v":1,"run":"1"}""", mapOf("cache-control" to "max-age=10", "etag" to "\"a\"")),
            TextHttp.Response(200, """{"v":1,"run":"2"}""", mapOf("cache-control" to "max-age=10", "etag" to "\"b\"")),
        )
        val clock = Clock(Instant.parse("2026-11-25T01:00:00Z"))
        val directory = Files.createTempDirectory("extended").toFile()
        val forecast = ExtendedForecast(directory, origin, clock::now)

        forecast.ensure()
        clock.instant = clock.instant.plusSeconds(11)
        forecast.ensure()

        assertEquals("""{"v":1,"run":"2"}""", File(directory, "extended.json").readText())
    }

    @Test
    fun anUnreadableResponseKeepsTheSavedCopy() {
        val origin = ScriptedHttp(
            TextHttp.Response(200, """{"v":1,"run":"1"}""", mapOf("cache-control" to "max-age=10", "etag" to "\"a\"")),
            TextHttp.Response(200, "<html>nope</html>", mapOf("cache-control" to "max-age=10")),
        )
        val clock = Clock(Instant.parse("2026-11-25T01:00:00Z"))
        val directory = Files.createTempDirectory("extended").toFile()
        val forecast = ExtendedForecast(directory, origin, clock::now)

        forecast.ensure()
        clock.instant = clock.instant.plusSeconds(11)
        forecast.ensure()

        assertEquals("""{"v":1,"run":"1"}""", File(directory, "extended.json").readText())
    }

    @Test
    fun aNetworkFailureKeepsTheSavedCopy() {
        val directory = Files.createTempDirectory("extended").toFile()
        val clock = Clock(Instant.parse("2026-11-25T01:00:00Z"))
        ExtendedForecast(
            directory,
            ScriptedHttp(TextHttp.Response(200, """{"v":1,"run":"1"}""", mapOf("cache-control" to "max-age=10"))),
            clock::now,
        ).ensure()
        clock.instant = clock.instant.plusSeconds(11)
        ExtendedForecast(directory, FailingHttp, clock::now).ensure()

        assertEquals("""{"v":1,"run":"1"}""", File(directory, "extended.json").readText())
        assertFalse(File(directory, "extended.json.tmp").exists())
    }

    @Test
    fun quarterDegreeKeyFindsThePointStoredUnderItsZone() {
        assertEquals("34.75,-86.75", extendedGridKey(34.73, -86.80, 0.25))
        val zones = JSONObject(
            """{"America/Chicago":{"34.75,-86.75":[[20261202,62,41,12,55,70,8,220,"rain"]]}}""",
        )
        val row = selectExtendedPoint(zones, "America/New_York", 34.73, -86.80, 0.25)

        assertEquals("rain", parseExtendedDays(row!!).single().condition)
        assertEquals(12, parseExtendedDays(row).single().precipHundredths)
    }

    private class Clock(var instant: Instant) {
        fun now(): Instant = instant
    }

    private class ScriptedHttp(private vararg val responses: TextHttp.Response) : TextSource {
        val sent = mutableListOf<Map<String, String>>()

        override fun get(url: String, headers: Map<String, String>): TextHttp.Response {
            sent += headers
            return responses[sent.lastIndex]
        }
    }

    private object FailingHttp : TextSource {
        override fun get(url: String, headers: Map<String, String>): TextHttp.Response {
            throw IOException("offline")
        }
    }
}
