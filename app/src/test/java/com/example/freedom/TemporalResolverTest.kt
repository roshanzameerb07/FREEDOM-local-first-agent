package com.example.freedom

import com.example.freedom.domain.query.RelativePeriod
import com.example.freedom.domain.query.TemporalConstraint
import com.example.freedom.domain.query.TemporalResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TemporalResolverTest {

    @Test
    fun testParsePeriodToken() {
        assertEquals(RelativePeriod.TODAY, TemporalResolver.parseRelativePeriodToken("TODAY"))
        assertEquals(RelativePeriod.YESTERDAY, TemporalResolver.parseRelativePeriodToken("YESTERDAY"))
        assertEquals(RelativePeriod.THIS_WEEK, TemporalResolver.parseRelativePeriodToken("THIS_WEEK"))
        assertEquals(RelativePeriod.LAST_WEEK, TemporalResolver.parseRelativePeriodToken("LAST_WEEK"))
        assertEquals(RelativePeriod.THIS_MONTH, TemporalResolver.parseRelativePeriodToken("THIS_MONTH"))
        assertEquals(RelativePeriod.LAST_MONTH, TemporalResolver.parseRelativePeriodToken("LAST_MONTH"))
        assertEquals(RelativePeriod.SINCE_MONDAY, TemporalResolver.parseRelativePeriodToken("SINCE_MONDAY"))
        assertEquals(RelativePeriod.LAST_SUNDAY, TemporalResolver.parseRelativePeriodToken("LAST_SUNDAY"))
    }

    @Test
    fun testResolveTodayRange() {
        val now = System.currentTimeMillis()
        val (start, end) = TemporalResolver.resolveRelative(RelativePeriod.TODAY, nowMillis = now)
        
        assertTrue(start <= now)
        assertTrue(end >= now)

        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(0, startCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, startCal.get(Calendar.MINUTE))
        assertEquals(0, startCal.get(Calendar.SECOND))

        val endCal = Calendar.getInstance().apply { timeInMillis = end }
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, endCal.get(Calendar.MINUTE))
        assertEquals(59, endCal.get(Calendar.SECOND))
    }
}
