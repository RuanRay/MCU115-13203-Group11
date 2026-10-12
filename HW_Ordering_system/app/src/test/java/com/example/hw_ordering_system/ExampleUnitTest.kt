package com.example.hw_ordering_system

import org.junit.Test

import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun orderIsCompleteOnlyWhenAllThreeArePicked() {
        assertTrue(Order("Burger", listOf("Fries"), "Cola").isComplete())
        assertFalse(Order(null, listOf("Fries"), "Cola").isComplete())
        assertFalse(Order("Burger", emptyList(), "Cola").isComplete())
        assertFalse(Order("Burger", listOf("Fries"), null).isComplete())
    }
}