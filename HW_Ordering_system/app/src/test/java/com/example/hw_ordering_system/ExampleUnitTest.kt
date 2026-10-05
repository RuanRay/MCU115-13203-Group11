package com.example.hw_ordering_system

import org.junit.Test

import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun orderIsCompleteOnlyWhenAllThreeArePicked() {
        assertTrue(isOrderComplete("Burger", listOf("Fries"), "Cola"))
        assertFalse(isOrderComplete(null, listOf("Fries"), "Cola"))
        assertFalse(isOrderComplete("Burger", emptyList(), "Cola"))
        assertFalse(isOrderComplete("Burger", listOf("Fries"), null))
    }
}