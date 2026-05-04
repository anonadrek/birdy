package se.birdy.content.build

data class ValidationError(
    val species: String,
    val rule: String,
    val message: String,
) {
    fun format(): String = "$species [$rule] $message"
}
