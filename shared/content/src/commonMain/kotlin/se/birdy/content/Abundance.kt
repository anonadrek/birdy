package se.birdy.content

enum class Abundance(
    val code: String,
) {
    ALLMÄN("allmän"),
    MINDRE_ALLMÄN("mindre allmän"),
    OVANLIG("ovanlig"),
    SÄLLSYNT("sällsynt"),
    ;

    companion object {
        fun fromCode(code: String): Abundance? = entries.firstOrNull { it.code == code }
    }
}
