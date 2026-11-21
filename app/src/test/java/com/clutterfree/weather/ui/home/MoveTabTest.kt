package com.clutterfree.weather.ui.home

import com.clutterfree.weather.weather.HomeCard
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveTabTest {
    private val order = listOf(HomeCard.TODAY, HomeCard.HOURLY, HomeCard.EXTENDED, HomeCard.RADAR)

    @Test
    fun swapsTabsAndLeavesOtherCardsInPlace() {
        assertEquals(
            listOf(HomeCard.TODAY, HomeCard.EXTENDED, HomeCard.HOURLY, HomeCard.RADAR),
            moveTab(order, 0, 1),
        )
    }

    @Test
    fun ignoresMovesPastTheEnds() {
        assertEquals(order, moveTab(order, 0, -1))
        assertEquals(order, moveTab(order, 2, 3))
    }
}
