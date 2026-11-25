package com.clutterfree.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetarTest {
    @Test
    fun weatherWinsOverSky() {
        assertEquals("rain", metarCondition("KMDQ 221515Z AUTO 00000KT 3 1/2SM -RA BR BKN011 OVC024 14/12 A3005 RMK A02 T01400124"))
    }

    @Test
    fun skyUsesTheMostCoveredLayer() {
        assertEquals("ovc", metarCondition("KMDQ 221415Z AUTO 00000KT 10SM SCT013 OVC025 14/12 A3005"))
        assertEquals("few", metarCondition("KORD 221451Z 27010KT 10SM FEW250 10/M02 A3012"))
        assertEquals("skc", metarCondition("KORD 221451Z 27010KT 10SM CLR 10/M02 A3012"))
        assertEquals("ovc", metarCondition("KORD 221451Z 00000KT 10SM VV010 M01/M01 A3012"))
    }

    @Test
    fun mapsPresentWeather() {
        assertEquals("tsra", metarCondition("KHSV 221553Z 18012KT 5SM +TSRA BKN030CB 24/20 A2990"))
        assertEquals("rain_showers", metarCondition("KHSV 221553Z 18012KT 9SM -SHRA SCT040 24/20 A2990"))
        assertEquals("fzra", metarCondition("KHSV 221553Z 03005KT 4SM -FZRA OVC008 M01/M02 A3020"))
        assertEquals("rain_snow", metarCondition("KHSV 221553Z 03005KT 2SM -RASN OVC008 01/00 A3020"))
        assertEquals("snow", metarCondition("KHSV 221553Z 03005KT 1SM SN OVC008 M03/M04 A3020"))
        assertEquals("fog", metarCondition("KHSV 221553Z 00000KT 1/4SM FG OVC002 10/10 A3020"))
        assertEquals("tsra_sct", metarCondition("KHSV 221553Z 18012KT 10SM VCTS SCT040CB 24/20 A2990"))
    }

    @Test
    fun ignoresRemarks() {
        assertEquals("skc", metarCondition("KORD 221451Z 27010KT 10SM CLR 10/M02 A3012 RMK AO2 RAB12E30"))
    }

    @Test
    fun severeSpeciIsASpecialReportForStormWeather() {
        assertTrue(severeSpeci("SPECI KMDQ 241712Z AUTO 24020G35KT 1SM +TSRA SQ GR BKN008"))
        assertTrue(severeSpeci("SPECI KMDQ 241712Z 18012KT 10SM VCTS FEW040CB"))
        assertFalse(severeSpeci("SPECI KMDQ 241712Z AUTO 00000KT 3SM BR BKN011"))
        assertFalse(severeSpeci("KMDQ 241715Z AUTO 24020G35KT 1SM +TSRA BKN008"))
    }

    @Test
    fun returnsNullWithoutSkyOrWeather() {
        assertNull(metarCondition("KMDQ 221515Z AUTO 00000KT 10SM 14/12 A3005"))
    }
}
