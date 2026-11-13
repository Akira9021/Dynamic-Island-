package com.akira.island.service

import org.junit.Assert.*
import org.junit.Test

class NetworkRateCounterTest {
    @Test fun twoBusySamplesAreRequiredAndDirectionsAreIndependent() {
        val c = NetworkRateCounter()
        assertNull(c.sample(0, 0, 0))
        assertNull(c.sample(4000, 2000, 2000))
        val r = c.sample(8000, 4000, 4000)!!
        assertEquals(2000.0, r.incoming, 0.0); assertEquals(1000.0, r.outgoing, 0.0)
    }
    @Test fun threeQuietSamplesHideWithoutFlickeringOnTheFirstPause() {
        val c = NetworkRateCounter(); c.sample(0, 0, 0); c.sample(4000, 0, 2000); c.sample(8000, 0, 4000)
        assertNotNull(c.sample(8000, 0, 6000)); assertNotNull(c.sample(8000, 0, 8000))
        assertNull(c.sample(8000, 0, 10000))
    }
    @Test fun unsupportedResetAndInvalidIntervalsNeverProduceSpikes() {
        val c = NetworkRateCounter()
        assertNull(c.sample(-1, 0, 0)); assertNull(c.sample(1000, 0, 1))
        assertNull(c.sample(500, 0, 2000)); assertNull(c.sample(4000, 0, 2000))
        assertNull(c.sample(8000, 0, 50000))
        c.reset(); assertNull(c.sample(1000000, 0, 50001))
    }
    @Test fun policyStopsForEveryDisabledCondition() {
        fun allowed(v: List<Boolean>) = networkRateAllowed(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8])
        val yes = listOf(true,true,true,true,true,false,false,true,false)
        assertTrue(allowed(yes))
        for (i in yes.indices) assertFalse("gate $i", allowed(yes.toMutableList().apply { this[i] = !this[i] }))
    }
}
