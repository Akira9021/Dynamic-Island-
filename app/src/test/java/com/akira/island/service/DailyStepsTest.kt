package com.akira.island.service
import org.junit.Assert.assertEquals
import org.junit.Test
class DailyStepsTest {
    @Test fun bootTotalIsNotTodaysCount() { val s=DailySteps(); assertEquals(0L,s.sample("day1",10000)); assertEquals(42L,s.sample("day1",10042)) }
    @Test fun rebootDoesNotSubtractSteps() { val s=DailySteps("day1",10000,42); assertEquals(42L,s.sample("day1",3)); assertEquals(44L,s.sample("day1",5)) }
    @Test fun newDayStartsAtZero() { val s=DailySteps("day1",10000,42); assertEquals(0L,s.sample("day2",11000)) }
}
