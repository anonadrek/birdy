package se.birdy.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingTest {
    @Test
    fun greeting_returns_swedish_welcome_for_sv_locale() {
        val greeting = Greeting().welcome("sv")
        assertEquals("Välkommen till Birdy", greeting)
    }

    @Test
    fun greeting_returns_english_welcome_for_en_locale() {
        val greeting = Greeting().welcome("en")
        assertEquals("Welcome to Birdy", greeting)
    }

    @Test
    fun greeting_falls_back_to_english_for_unknown_locale() {
        val greeting = Greeting().welcome("fr")
        assertEquals("Welcome to Birdy", greeting)
    }
}
