package com.clutterfree.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UsPlaceIndexTest {
    private val index = UsPlaceIndex(
        listOf(
            UsPlace("Chicago", "IL", 41.84, -87.68),
            UsPlace("Charlotte", "NC", 35.21, -80.84),
            UsPlace("Chicago Heights", "IL", 41.51, -87.64),
            UsPlace("San José", "CA", 37.34, -121.89),
            UsPlace("Springfield", "IL", 39.78, -89.65),
            UsPlace("Springfield", "MA", 42.10, -72.59),
        ),
    )

    @Test
    fun prefixSuggestionsPreferTheOrderOfTheList() {
        assertEquals("Chicago, IL", index.suggest("chi").first().label)
        assertEquals(listOf("Chicago", "Chicago Heights"), index.suggest("chicago, il").map { it.name })
    }

    @Test
    fun stateAndAccentsArePartOfTheMatch() {
        assertEquals("San José", index.suggest("san jose").single().name)
        assertEquals("Springfield", index.suggest("springfield ma").single().name)
        assertEquals("MA", index.suggest("springfield, ma").single().state)
    }

    @Test
    fun bundledPlacesPutChicagoFirst() {
        val file = listOf(
            File("src/main/assets/us_places.txt"),
            File("app/src/main/assets/us_places.txt"),
        ).first { it.exists() }
        val places = file.inputStream().use { UsPlaceIndex.load(it) }

        assertEquals("Chicago, IL", places.suggest("chicago").first().label)
        assertEquals("New York, NY", places.suggest("new york").first().label)
    }

    @Test
    fun shortOrUnknownQueriesStayEmpty() {
        assertTrue(index.suggest("c").isEmpty())
        assertTrue(index.suggest("zzzz").isEmpty())
    }
}
