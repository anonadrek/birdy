package se.birdy.content

enum class Locale(
    val code: String,
) {
    SV("sv"),
    EN("en"),
    ;

    companion object {
        fun fromCode(code: String): Locale = entries.first { it.code == code }
    }
}
