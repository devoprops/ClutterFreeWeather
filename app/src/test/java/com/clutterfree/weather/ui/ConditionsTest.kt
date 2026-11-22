package com.clutterfree.weather.ui

import com.devoprop.clutterfreeweather.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ConditionsTest {
    @Test
    fun iconCodesMapToConditionStrings() {
        assertEquals(R.string.condition_sct, conditionString("sct"))
        assertEquals(R.string.condition_tsra, conditionString("tsra_sct"))
        assertEquals(R.string.condition_snow, conditionString("blizzard"))
        assertEquals(R.string.condition_rain_snow, conditionString("rain_snow"))
        assertEquals(R.string.weather_icon, conditionString("not_a_condition"))
    }
}
