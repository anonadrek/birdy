package se.birdy.domain

class Greeting {
    fun welcome(locale: String): String =
        when (locale) {
            "sv" -> "Välkommen till Birdy"
            else -> "Welcome to Birdy"
        }
}
