package com.desideri.familybalance

import com.desideri.familybalance.data.arrotonda
import org.junit.Assert.assertEquals
import org.junit.Test

class StipendiTest {
    @Test
    fun arrotondamenti() {
        assertEquals(760L, arrotonda(764, 1, false))
        assertEquals(770L, arrotonda(768, 1, false))
        assertEquals(760L, arrotonda(762, 1, true))
        assertEquals(765L, arrotonda(763, 1, true))
        assertEquals(700L, arrotonda(724, 0, true))
        assertEquals(750L, arrotonda(725, 0, true))
        assertEquals(800L, arrotonda(750, 0, false))
    }
}
