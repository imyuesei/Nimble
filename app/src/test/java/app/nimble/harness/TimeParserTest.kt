package app.nimble.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeParserTest {
    @Test fun clockForms() {
        assertEquals(7 to 0, TimeParser.clock("7am"))
        assertEquals(7 to 0, TimeParser.clock("7"))
        assertEquals(18 to 30, TimeParser.clock("6:30 pm"))
        assertEquals(18 to 30, TimeParser.clock("6.30 p.m."))
        assertEquals(19 to 0, TimeParser.clock("19:00"))
        assertEquals(7 to 30, TimeParser.clock("seven thirty"))
        assertEquals(7 to 45, TimeParser.clock("seven forty five am"))
        assertEquals(6 to 30, TimeParser.clock("half past 6"))
        assertEquals(6 to 45, TimeParser.clock("quarter to 7"))
        assertEquals(12 to 0, TimeParser.clock("noon"))
        assertEquals(0 to 0, TimeParser.clock("12 am"))
        assertEquals(7 to 30, TimeParser.clock("730"))
        assertEquals(20 to 0, TimeParser.clock("8 in the evening"))
        assertEquals(15 to 0, TimeParser.clock("3 in the afternoon"))
        assertNull(TimeParser.clock("whenever"))
    }

    @Test fun durations() {
        assertEquals(600, TimeParser.durationSeconds("10 minutes"))
        assertEquals(600, TimeParser.durationSeconds("ten minutes"))
        assertEquals(5400, TimeParser.durationSeconds("1 hour 30 min"))
        assertEquals(5400, TimeParser.durationSeconds("an hour and a half"))
        assertEquals(1800, TimeParser.durationSeconds("half an hour"))
        assertEquals(90, TimeParser.durationSeconds("90 seconds"))
        assertEquals(1500, TimeParser.durationSeconds("twenty five minutes"))
        assertEquals(300, TimeParser.durationSeconds("5"))
        assertEquals(300, TimeParser.durationSeconds("5min"))
        assertNull(TimeParser.durationSeconds("a while"))
    }
}
